package dev.sdlc.workflow.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.dependency.Dependency;
import dev.sdlc.workflow.dependency.DependencyKind;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.dependency.DependencyStatus;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicStatus;
import dev.sdlc.workflow.epic.EpicWorkflow;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TicketWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private record Fixture(TicketWorkflowService tickets, InMemoryDependencyRepository dependencies) {
    }

    private Fixture fixture() {
        Clock clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        InMemoryDependencyRepository dependencies = new InMemoryDependencyRepository();
        TicketWorkflowService ticketService = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                dependencies, audits, clock);
        return new Fixture(ticketService, dependencies);
    }

    @Test
    void createsTicketsOnlyOnActiveEpics() {
        Fixture fixture = fixture();
        TicketWorkflow ticket = fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        assertEquals(TicketDeliveryStatus.PLANNED, ticket.status());
        assertEquals(Channel.API, ticket.channel());
    }

    @Test
    void rejectsDuplicateTicketIds() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class,
                () -> fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.WEB, "EMP-100", "corr-2"));
    }

    @Test
    void followsTheDeliveryTransitionPath() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        long version = 0;
        for (TicketDeliveryStatus next : new TicketDeliveryStatus[] {
                TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.CI_PASSED }) {
            TicketWorkflow moved = fixture.tickets().transition("M2-API-1", version, next, "EMP-100", "corr-2");
            assertEquals(next, moved.status());
            version = moved.version();
        }
        assertEquals(TicketDeliveryStatus.CI_PASSED,
                fixture.tickets().ticket("M2-API-1").status());
    }

    @Test
    void rejectsInvalidTransitions() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        assertThrows(WorkflowConflictException.class, () -> fixture.tickets()
                .transition("M2-API-1", 0, TicketDeliveryStatus.MERGED, "EMP-100", "corr-2"));
    }

    @Test
    void mergeIsBlockedByUnresolvedDependency() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        fixture.tickets().create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-2");
        long version = 0;
        for (TicketDeliveryStatus next : new TicketDeliveryStatus[] {
                TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.CI_PASSED }) {
            version = fixture.tickets().transition("M2-WEB-1", version, next, "EMP-100", "corr-2").version();
        }
        DependencyService dependencyService = new DependencyService(new InMemoryEpicWorkflowRepository(),
                fixture.tickets(), fixture.dependencies(), new InMemoryDomainAuditEventRepository(), Clock.systemUTC());
        fixture.dependencies().save(new Dependency("DEP-1", "EPIC-M2-1", "M2-API-1", "M2-WEB-1",
                DependencyKind.REQUIRES_BEFORE, DependencyStatus.BLOCKING, 0, NOW));

        long mergeVersion = version;
        assertThrows(WorkflowConflictException.class, () -> fixture.tickets()
                .transition("M2-WEB-1", mergeVersion, TicketDeliveryStatus.MERGED, "EMP-100", "corr-3"));
    }

    @Test
    void mergeSucceedsAfterDependencyResolves() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        fixture.tickets().create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-2");
        long version = 0;
        for (TicketDeliveryStatus next : new TicketDeliveryStatus[] {
                TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.CI_PASSED }) {
            version = fixture.tickets().transition("M2-WEB-1", version, next, "EMP-100", "corr-2").version();
        }
        Dependency dependency = fixture.dependencies().save(new Dependency("DEP-1", "EPIC-M2-1", "M2-API-1",
                "M2-WEB-1", DependencyKind.REQUIRES_BEFORE, DependencyStatus.RESOLVED, 0, NOW));

        assertEquals(DependencyStatus.RESOLVED, dependency.status());
        TicketWorkflow merged = fixture.tickets()
                .transition("M2-WEB-1", version, TicketDeliveryStatus.MERGED, "EMP-100", "corr-3");
        assertEquals(TicketDeliveryStatus.MERGED, merged.status());
    }

    @Test
    void changeFlagMovesWithAck() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        TicketWorkflow flagged = fixture.tickets().markChangePending("M2-API-1", "EMP-100", "corr-2");
        assertEquals(true, flagged.pendingChangeConfirmation());
        TicketWorkflow acked = fixture.tickets().ackChange("M2-API-1", flagged.version(), "EMP-100", "corr-3");
        assertEquals(false, acked.pendingChangeConfirmation());
    }
}
