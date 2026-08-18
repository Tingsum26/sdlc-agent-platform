package dev.sdlc.workflow.skip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class SkipServiceTest {

    private record Fixture(SkipService skips, WorkflowTaskService tasks) {
    }

    private Fixture fixture() {
        Clock clock = Clock.systemUTC();
        InMemoryWorkflowTaskRepository tasks = new InMemoryWorkflowTaskRepository();
        InMemoryAuditEventRepository audits = new InMemoryAuditEventRepository();
        WorkflowTaskService taskService = new WorkflowTaskService(tasks, audits, new TaskTransitionPolicy(), clock);
        return new Fixture(new SkipService(taskService, new InMemorySkipAttestationRepository(), clock), taskService);
    }

    @Test
    void skipsAWaitingStageWithAttestation() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-1", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123", "EMP-100", "corr-1");

        SkipResult result = fixture.skips().skip("TASK-M2-1", 0, "Fictional fast-track",
                "Fictional architect", "EMP-100", "DEVELOPER", "corr-2");

        assertEquals(TaskStatus.COMPLETED, result.task().status());
        assertEquals("DESIGN", result.attestation().stageType());
        assertEquals("Fictional fast-track", result.attestation().reason());
        assertEquals(1, fixture.skips().listByTask("TASK-M2-1").size());
    }

    @Test
    void rejectsSkippingACompletedStage() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-2", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-b", "EMP-100", "corr-1");
        fixture.skips().skip("TASK-M2-2", 0, "r", "w", "EMP-100", "DEVELOPER", "corr-2");

        assertThrows(dev.sdlc.workflow.task.IllegalTaskTransitionException.class, () -> fixture.skips()
                .skip("TASK-M2-2", 1, "r2", "w", "EMP-100", "DEVELOPER", "corr-3"));
    }

    @Test
    void rejectsBlankReason() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-3", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-c", "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class, () -> fixture.skips()
                .skip("TASK-M2-3", 0, " ", "w", "EMP-100", "DEVELOPER", "corr-2"));
    }

    @Test
    void skipsARunningStageAndKeepsTheClaimRecord() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-4", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-d", "EMP-100", "corr-1");
        fixture.tasks().claimTask("TASK-M2-4", "EMP-100", java.time.Duration.ofMinutes(15), 0, "corr-2");

        SkipResult result = fixture.skips().skip("TASK-M2-4", 1, "Fictional fast-track",
                "Fictional architect", "EMP-100", "DEVELOPER", "corr-3");

        assertEquals(TaskStatus.COMPLETED, result.task().status());
        assertEquals("DESIGN", result.attestation().stageType());
    }

    @Test
    void skipsAWaitingForConfirmationStage() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-5", TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-e", "EMP-100", "corr-1");
        fixture.tasks().claimTask("TASK-M2-5", "EMP-100", java.time.Duration.ofMinutes(15), 0, "corr-2");
        fixture.tasks().transition("TASK-M2-5", TaskStatus.LOCAL_COPILOT_RUNNING, TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                1, "EMP-100", "corr-3");

        SkipResult result = fixture.skips().skip("TASK-M2-5", 2, "Fictional fast-track",
                "Fictional architect", "EMP-100", "DEVELOPER", "corr-4");

        assertEquals(TaskStatus.COMPLETED, result.task().status());
    }

    @Test
    void rejectsBlankActorRole() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-6", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-f", "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class, () -> fixture.skips()
                .skip("TASK-M2-6", 0, "Fictional fast-track", "Fictional architect", "EMP-100", " ", "corr-2"));
    }
}
