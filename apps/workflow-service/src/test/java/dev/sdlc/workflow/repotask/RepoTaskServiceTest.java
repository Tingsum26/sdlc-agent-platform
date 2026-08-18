package dev.sdlc.workflow.repotask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class RepoTaskServiceTest {

    private RepoTaskService service() {
        Clock clock = Clock.systemUTC();
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        TicketWorkflowService tickets = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                new InMemoryDependencyRepository(), audits, clock);
        tickets.create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-3");
        return new RepoTaskService(tickets, new InMemoryRepoTaskRepository(), audits, clock);
    }

    @Test
    void createsPlannedRepoTask() {
        RepoTask task = service().create("M2-API-1", "REPO_A", "0123456789abcdef", "EMP-100", "corr-1");
        assertEquals(RepoTaskStatus.PLANNED, task.status());
        assertEquals("M2-API-1", task.ticketId());
    }

    @Test
    void transitionsAndRejectsInvalidMoves() {
        RepoTaskService service = service();
        RepoTask task = service.create("M2-API-1", "REPO_A", "0123456789abcdef", "EMP-100", "corr-1");
        RepoTask progressed = service.transition(task.repoTaskId(), 0, RepoTaskStatus.IN_PROGRESS, "EMP-100", "corr-2");
        assertEquals(RepoTaskStatus.IN_PROGRESS, progressed.status());
        assertThrows(IllegalStateException.class, () -> service
                .transition(task.repoTaskId(), 1, RepoTaskStatus.MERGED, "EMP-100", "corr-3"));
    }

    @Test
    void rejectsUnknownTicket() {
        assertThrows(IllegalArgumentException.class,
                () -> service().create("M2-NOPE", "REPO_A", "0123456789abcdef", "EMP-100", "corr-1"));
    }
}
