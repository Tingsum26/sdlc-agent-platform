package dev.sdlc.workflow.epic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.change.ChangeRequestStatus;
import dev.sdlc.workflow.change.ChangeUrgency;
import dev.sdlc.workflow.change.EpicChangeRequest;
import dev.sdlc.workflow.dependency.Dependency;
import dev.sdlc.workflow.dependency.DependencyKind;
import dev.sdlc.workflow.dependency.DependencyStatus;
import dev.sdlc.workflow.ticket.TicketDeliveryStatus;
import dev.sdlc.workflow.ticket.TicketWorkflow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class M2DomainRecordsTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void epicTransitionsBumpVersionAndTimestamp() {
        EpicWorkflow created = new EpicWorkflow("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING",
                EpicStatus.CREATED, 0, NOW, NOW);
        EpicWorkflow active = created.transitionedTo(EpicStatus.ACTIVE, NOW.plusSeconds(1));
        assertEquals(EpicStatus.ACTIVE, active.status());
        assertEquals(1, active.version());
        assertEquals(NOW.plusSeconds(1), active.updatedAt());
    }

    @Test
    void ticketTransitionKeepsChangeFlag() {
        TicketWorkflow ticket = new TicketWorkflow("M2-API-1", "EPIC-M2-1", Channel.API,
                TicketDeliveryStatus.PLANNED, true, 0, NOW, NOW);
        TicketWorkflow moved = ticket.transitionedTo(TicketDeliveryStatus.IN_ANALYSIS, NOW.plusSeconds(1));
        assertEquals(TicketDeliveryStatus.IN_ANALYSIS, moved.status());
        assertEquals(true, moved.pendingChangeConfirmation());
        assertEquals(1, moved.version());
    }

    @Test
    void changeRequestApprovalCompletesAtRequiredCount() {
        EpicChangeRequest request = new EpicChangeRequest("CR-1", "EPIC-M2-1", "Fictional scope change",
                ChangeUrgency.URGENT, "Fictional detail", List.of("M2-API-1"), List.of(), 2,
                ChangeRequestStatus.DRAFT, 0, NOW, NOW);
        EpicChangeRequest one = request.withApproval("BUSINESS_OWNER", NOW.plusSeconds(1));
        assertEquals(ChangeRequestStatus.DRAFT, one.status());
        assertEquals(1, one.approvedRoles().size());
        EpicChangeRequest two = one.withApproval("TECHNICAL_OWNER", NOW.plusSeconds(2));
        assertEquals(ChangeRequestStatus.APPROVED, two.status());
        assertEquals(2, two.approvedRoles().size());
    }

    @Test
    void dependencyResolvedBumpsVersion() {
        Dependency blocking = new Dependency("DEP-1", "EPIC-M2-1", "M2-API-1", "M2-WEB-1",
                DependencyKind.REQUIRES_BEFORE, DependencyStatus.BLOCKING, 0, NOW);
        Dependency resolved = blocking.resolved(NOW.plusSeconds(1));
        assertEquals(DependencyStatus.RESOLVED, resolved.status());
        assertEquals(1, resolved.version());
    }

    @Test
    void epicRoundTripsThroughInMemoryRepository() {
        InMemoryEpicWorkflowRepository repository = new InMemoryEpicWorkflowRepository();
        EpicWorkflow created = new EpicWorkflow("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING",
                EpicStatus.CREATED, 0, NOW, NOW);
        repository.save(created);
        EpicWorkflow reloaded = repository.findById("EPIC-M2-1").orElseThrow();
        assertEquals(created, reloaded);
    }
}
