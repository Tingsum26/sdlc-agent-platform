package dev.sdlc.workflow.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeRequestServiceTest {

    private record Fixture(ChangeRequestService changes, TicketWorkflowService tickets) {
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
        ChangeRequestService changes = new ChangeRequestService(epics, tickets, new InMemoryChangeRequestRepository(),
                audits, clock);
        return new Fixture(changes, tickets);
    }

    @Test
    void dualRoleApprovalMarksAffectedTickets() {
        Fixture fixture = fixture();
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "Fictional scope change",
                ChangeUrgency.URGENT, "Fictional detail", List.of("M2-API-1"), "EMP-100", "corr-1");
        assertEquals(ChangeRequestStatus.DRAFT, created.status());

        EpicChangeRequest one = fixture.changes().approve(created.changeRequestId(), 0, "EMP-100",
                "BUSINESS_OWNER", "corr-2");
        assertEquals(ChangeRequestStatus.DRAFT, one.status());
        assertEquals(1, one.approvedRoles().size());

        EpicChangeRequest two = fixture.changes().approve(created.changeRequestId(), 1, "EMP-100",
                "TECHNICAL_OWNER", "corr-3");
        assertEquals(ChangeRequestStatus.APPROVED, two.status());
        assertEquals(true, fixture.tickets().ticket("M2-API-1").pendingChangeConfirmation());
    }

    @Test
    void rejectsDuplicateRoleApproval() {
        Fixture fixture = fixture();
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "Fictional scope change",
                ChangeUrgency.STANDARD, "Fictional detail", List.of(), "EMP-100", "corr-1");
        fixture.changes().approve(created.changeRequestId(), 0, "EMP-100", "BUSINESS_OWNER", "corr-2");
        assertThrows(WorkflowConflictException.class, () -> fixture.changes()
                .approve(created.changeRequestId(), 1, "EMP-100", "BUSINESS_OWNER", "corr-3"));
    }

    @Test
    void rejectsUnknownRolesAndUnknownEpics() {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.changes().create("EPIC-NOPE", "r",
                ChangeUrgency.STANDARD, "d", List.of(), "EMP-100", "corr-1"));
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "r", ChangeUrgency.STANDARD, "d",
                List.of(), "EMP-100", "corr-2");
        assertThrows(IllegalArgumentException.class, () -> fixture.changes()
                .approve(created.changeRequestId(), 0, "EMP-100", "DEVELOPER", "corr-3"));
    }

    @Test
    void rejectFreezesTheRequest() {
        Fixture fixture = fixture();
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "r", ChangeUrgency.STANDARD, "d",
                List.of(), "EMP-100", "corr-1");
        EpicChangeRequest rejected = fixture.changes().reject(created.changeRequestId(), 0, "EMP-100", "corr-2");
        assertEquals(ChangeRequestStatus.REJECTED, rejected.status());
        assertThrows(WorkflowConflictException.class, () -> fixture.changes()
                .approve(created.changeRequestId(), 1, "EMP-100", "BUSINESS_OWNER", "corr-3"));
    }
}
