package dev.sdlc.workflow.epic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class EpicWorkflowServiceTest {

    private EpicWorkflowService service() {
        return new EpicWorkflowService(new InMemoryEpicWorkflowRepository(),
                new InMemoryDomainAuditEventRepository(), Clock.systemUTC());
    }

    @Test
    void createsAnEpicInCreatedStatus() {
        EpicWorkflow epic = service().create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        assertEquals(EpicStatus.CREATED, epic.status());
        assertEquals(0, epic.version());
    }

    @Test
    void rejectsDuplicateEpicIds() {
        EpicWorkflowService service = service();
        service.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class,
                () -> service.create("EPIC-M2-1", "Other", "ACCOUNT_OPENING", "EMP-100", "corr-2"));
    }

    @Test
    void activatesWithExactVersion() {
        EpicWorkflowService service = service();
        service.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        EpicWorkflow active = service.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        assertEquals(EpicStatus.ACTIVE, active.status());
        assertEquals(1, active.version());
    }

    @Test
    void rejectsStaleActivation() {
        EpicWorkflowService service = service();
        service.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        assertThrows(WorkflowConflictException.class, () -> service.activate("EPIC-M2-1", 5, "EMP-100", "corr-2"));
    }
}
