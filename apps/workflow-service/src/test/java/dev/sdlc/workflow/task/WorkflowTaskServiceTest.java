package dev.sdlc.workflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    private InMemoryWorkflowTaskRepository tasks;
    private InMemoryAuditEventRepository auditEvents;
    private WorkflowTaskService service;

    @BeforeEach
    void setUp() {
        tasks = new InMemoryWorkflowTaskRepository();
        auditEvents = new InMemoryAuditEventRepository();
        service = new WorkflowTaskService(
                tasks,
                auditEvents,
                new TaskTransitionPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsOneWaitingTaskForTheSameIdempotencyKey() {
        WorkflowScope scope = new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567");

        WorkflowTask first = service.createTask("TASK-001", TaskType.REQUIREMENT_ANALYSIS, scope, "create-demo-123", "user-1", "corr-1");
        WorkflowTask second = service.createTask("TASK-002", TaskType.REQUIREMENT_ANALYSIS, scope, "create-demo-123", "user-1", "corr-2");

        assertThat(first.taskId()).isEqualTo("TASK-001");
        assertThat(second.taskId()).isEqualTo(first.taskId());
        assertThat(first.status()).isEqualTo(TaskStatus.WAITING_FOR_LOCAL_COPILOT);
        assertThat(tasks.findAll()).hasSize(1);
        assertThat(auditEvents.findByTaskId(first.taskId())).extracting(AuditEvent::action).containsExactly("TASK_CREATED");
    }

    @Test
    void claimsAWaitingTaskWithAnExpiringLease() {
        WorkflowTask created = createTask();

        WorkflowTask claimed = service.claimTask(created.taskId(), "developer-1", Duration.ofMinutes(15), created.version(), "corr-2");

        assertThat(claimed.status()).isEqualTo(TaskStatus.LOCAL_COPILOT_RUNNING);
        assertThat(claimed.assigneeId()).isEqualTo("developer-1");
        assertThat(claimed.leaseExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(claimed.version()).isEqualTo(1);
    }

    @Test
    void rejectsASecondClaimInsteadOfStealingTheLease() {
        WorkflowTask claimed = service.claimTask(createTask().taskId(), "developer-1", Duration.ofMinutes(15), 0, "corr-2");

        assertThatThrownBy(() -> service.claimTask(claimed.taskId(), "developer-2", Duration.ofMinutes(15), claimed.version(), "corr-3"))
                .isInstanceOf(IllegalTaskTransitionException.class);
    }

    @Test
    void releasesAnExpiredLeaseBackToTheWaitingQueue() {
        WorkflowTask claimed = service.claimTask(createTask().taskId(), "developer-1", Duration.ofMinutes(15), 0, "corr-2");

        int released = service.releaseExpiredLeases(NOW.plus(Duration.ofMinutes(16)), "system", "corr-3");

        WorkflowTask task = tasks.findById(claimed.taskId()).orElseThrow();
        assertThat(released).isEqualTo(1);
        assertThat(task.status()).isEqualTo(TaskStatus.WAITING_FOR_LOCAL_COPILOT);
        assertThat(task.assigneeId()).isNull();
        assertThat(task.leaseExpiresAt()).isNull();
    }

    @Test
    void rejectsAStaleExpectedVersion() {
        WorkflowTask created = createTask();
        service.claimTask(created.taskId(), "developer-1", Duration.ofMinutes(15), 0, "corr-2");

        assertThatThrownBy(() -> service.transition(created.taskId(), TaskStatus.LOCAL_COPILOT_RUNNING,
                TaskStatus.WAITING_FOR_USER_CONFIRMATION, 0, "developer-1", "corr-3"))
                .isInstanceOf(StaleTaskVersionException.class);
    }

    @Test
    void rejectsRestartingACompletedTask() {
        WorkflowTask task = service.createTask(
                "TASK-MANUAL-E2E",
                TaskType.MANUAL_E2E,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "manual-e2e-demo-123",
                "user-1",
                "corr-1");
        task = service.claimTask(task.taskId(), "developer-1", Duration.ofMinutes(15), task.version(), "corr-2");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_USER_CONFIRMATION, task.version(), "developer-1", "corr-3");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_APPROVAL, task.version(), "developer-1", "corr-4");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_CI, task.version(), "approver-1", "corr-5");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_MANUAL_E2E, task.version(), "system", "corr-6");
        task = service.transition(task.taskId(), task.status(), TaskStatus.COMPLETED, task.version(), "qa-1", "corr-7");

        WorkflowTask completed = task;
        assertThatThrownBy(() -> service.transition(completed.taskId(), TaskStatus.COMPLETED,
                TaskStatus.LOCAL_COPILOT_RUNNING, completed.version(), "developer-1", "corr-8"))
                .isInstanceOf(IllegalTaskTransitionException.class);
    }

    @Test
    void rejectsSendingAnImplementationTaskToTheManualE2eGate() {
        WorkflowTask task = service.createTask(
                "TASK-IMPLEMENTATION",
                TaskType.IMPLEMENTATION,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "implementation-demo-123",
                "user-1",
                "corr-1");
        task = service.claimTask(task.taskId(), "developer-1", Duration.ofMinutes(15), task.version(), "corr-2");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                task.version(), "developer-1", "corr-3");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_APPROVAL,
                task.version(), "developer-1", "corr-4");
        task = service.transition(task.taskId(), task.status(), TaskStatus.WAITING_FOR_CI,
                task.version(), "reviewer-1", "corr-5");

        WorkflowTask waitingForCi = task;
        assertThatThrownBy(() -> service.transition(waitingForCi.taskId(), waitingForCi.status(),
                TaskStatus.WAITING_FOR_MANUAL_E2E, waitingForCi.version(), "ci-reader", "corr-6"))
                .isInstanceOf(IllegalTaskTransitionException.class);
    }

    @Test
    void preservesBlockingForALegacyApprovalOnlyTaskAlreadyWaitingForCi() {
        tasks.save(new WorkflowTask(
                "TASK-LEGACY-CI",
                TaskType.REQUIREMENT_ANALYSIS,
                TaskStatus.WAITING_FOR_CI,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "legacy-ci-demo-123",
                "developer-1",
                null,
                4,
                NOW,
                NOW));

        WorkflowTask blocked = service.transition("TASK-LEGACY-CI", TaskStatus.WAITING_FOR_CI,
                TaskStatus.BLOCKED, 4, "ci-reader", "corr-legacy");

        assertThat(blocked.status()).isEqualTo(TaskStatus.BLOCKED);
    }

    private WorkflowTask createTask() {
        return service.createTask(
                "TASK-001",
                TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "create-demo-123",
                "user-1",
                "corr-1");
    }
}
