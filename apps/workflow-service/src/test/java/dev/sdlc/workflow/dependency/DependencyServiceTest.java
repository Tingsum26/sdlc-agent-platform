package dev.sdlc.workflow.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class DependencyServiceTest {

    private record Fixture(DependencyService dependencies) {
    }

    private Fixture fixture() {
        Clock clock = Clock.systemUTC();
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        TicketWorkflowService tickets = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                new InMemoryDependencyRepository(), audits, clock);
        tickets.create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-3");
        tickets.create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-4");
        return new Fixture(new DependencyService(epics, tickets, new InMemoryDependencyRepository(), audits, clock));
    }

    @Test
    void addsBlockingDependencyAndIsIdempotent() {
        Fixture fixture = fixture();
        Dependency first = fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-WEB-1", "EMP-100", "corr-1");
        Dependency second = fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-WEB-1", "EMP-100", "corr-2");
        assertEquals(first.dependencyId(), second.dependencyId());
        assertEquals(DependencyStatus.BLOCKING, second.status());
    }

    @Test
    void rejectsSelfLoopAndUnknownTickets() {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class,
                () -> fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-API-1", "EMP-100", "corr-1"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-NOPE", "EMP-100", "corr-2"));
    }

    @Test
    void resolvesWithExactVersion() {
        Fixture fixture = fixture();
        Dependency added = fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-WEB-1", "EMP-100", "corr-1");
        Dependency resolved = fixture.dependencies().resolve(added.dependencyId(), 0, "EMP-100", "corr-2");
        assertEquals(DependencyStatus.RESOLVED, resolved.status());
        assertThrows(WorkflowConflictException.class,
                () -> fixture.dependencies().resolve(added.dependencyId(), 0, "EMP-100", "corr-3"));
    }
}
