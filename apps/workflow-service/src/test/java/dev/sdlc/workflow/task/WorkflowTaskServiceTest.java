package dev.sdlc.workflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({ "REAL,SIMULATED_PASS", "SIMULATED_PASS,REAL" })
    void doesNotReuseALegacyTaskAcrossEvidenceClassifications(
            EvidenceClassification existingClassification,
            EvidenceClassification requestedClassification) {
        WorkflowScope scope = new WorkflowScope("DEMO-CLASS", "REPO_A", "same-ref");
        String legacyKey = "ticket:DEMO-CLASS:same-ref";
        service.createTask("TASK-LEGACY", TaskType.REQUIREMENT_ANALYSIS, scope, legacyKey, null,
                existingClassification, "seed", "corr-seed");

        WorkflowTask created = service.createTask("TASK-CURRENT", TaskType.REQUIREMENT_ANALYSIS, scope,
                "ticket:DEMO-CLASS:REPO_A:same-ref:REQUIREMENT_ANALYSIS:" + requestedClassification,
                legacyKey, requestedClassification, "requester", "corr-current");

        assertThat(created.taskId()).isEqualTo("TASK-CURRENT");
        assertThat(created.evidenceClassification()).isEqualTo(requestedClassification);
        assertThat(tasks.findAll()).hasSize(2);
    }

    @Test
    void doesNotReuseALegacyTaskOutsideTheExactScope() {
        String legacyKey = "ticket:DEMO-SCOPE:legacy-ref";
        service.createTask("TASK-LEGACY", TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("DEMO-SCOPE", "REPO_A", "legacy-ref"), legacyKey, null,
                EvidenceClassification.REAL, "seed", "corr-seed");

        WorkflowTask created = service.createTask("TASK-NEW-SCOPE", TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("DEMO-SCOPE", "REPO_A", "different-ref"), "new-key", legacyKey,
                EvidenceClassification.REAL, "requester", "corr-current");

        assertThat(created.taskId()).isEqualTo("TASK-NEW-SCOPE");
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
    void restoresTheExactTaskAndAuditStateWhenAuditPersistenceFailsAfterAppend() {
        InMemoryWorkflowTaskRepository faultTasks = new InMemoryWorkflowTaskRepository();
        AppendThenFailAuditRepository faultAudits = new AppendThenFailAuditRepository();
        WorkflowTaskService faultService = new WorkflowTaskService(
                faultTasks, faultAudits, new TaskTransitionPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
        WorkflowTask created = faultService.createTask("TASK-AUDIT-ROLLBACK", TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("DEMO-AUDIT", "REPO_A", "audit-ref"),
                "audit-rollback", "author", "corr-create");
        faultAudits.failAfterAppend = true;

        assertThatThrownBy(() -> faultService.claimTask(created.taskId(), "developer-1",
                Duration.ofMinutes(15), created.version(), "corr-claim"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit persistence");

        WorkflowTask restored = faultTasks.findById(created.taskId()).orElseThrow();
        assertThat(restored.status()).isEqualTo(TaskStatus.WAITING_FOR_LOCAL_COPILOT);
        assertThat(restored.version()).isZero();
        assertThat(restored.assigneeId()).isNull();
        assertThat(faultAudits.findByTaskId(created.taskId()))
                .extracting(AuditEvent::action)
                .containsExactly("TASK_CREATED");
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

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {
            "REQUIREMENT_ANALYSIS", "DESIGN", "DELIVERY_COORDINATION", "ONBOARDING_SYNC" })
    void completesLegacyApprovalOnlyTasksAlreadyWaitingForCiAfterPassedCi(TaskType taskType) {
        tasks.save(new WorkflowTask(
                "TASK-LEGACY-CI",
                taskType,
                TaskStatus.WAITING_FOR_CI,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "legacy-ci-demo-123",
                "developer-1",
                null,
                4,
                NOW,
                NOW));

        WorkflowTask completed = service.transitionAfterPassedCi(
                "TASK-LEGACY-CI", 4, "ci-reader", "corr-legacy");

        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {
            "REQUIREMENT_ANALYSIS", "DESIGN", "DELIVERY_COORDINATION", "ONBOARDING_SYNC" })
    void retainsBlockAndCancelRecoveryForLegacyApprovalOnlyTasks(TaskType taskType) {
        saveLegacyTask("TASK-CI-BLOCK", taskType, TaskStatus.WAITING_FOR_CI, 4);
        saveLegacyTask("TASK-CI-CANCEL", taskType, TaskStatus.WAITING_FOR_CI, 4);
        saveLegacyTask("TASK-MANUAL-BLOCK", taskType, TaskStatus.WAITING_FOR_MANUAL_E2E, 5);
        saveLegacyTask("TASK-MANUAL-CANCEL", taskType, TaskStatus.WAITING_FOR_MANUAL_E2E, 5);

        WorkflowTask ciBlocked = service.transition("TASK-CI-BLOCK", TaskStatus.WAITING_FOR_CI,
                TaskStatus.BLOCKED, 4, "ci-reader", "corr-block");
        WorkflowTask ciCancelled = service.transition("TASK-CI-CANCEL", TaskStatus.WAITING_FOR_CI,
                TaskStatus.CANCELLED, 4, "migration-operator", "corr-cancel");
        WorkflowTask manualBlocked = service.transition("TASK-MANUAL-BLOCK", TaskStatus.WAITING_FOR_MANUAL_E2E,
                TaskStatus.BLOCKED, 5, "migration-operator", "corr-block");
        WorkflowTask manualCancelled = service.transition("TASK-MANUAL-CANCEL", TaskStatus.WAITING_FOR_MANUAL_E2E,
                TaskStatus.CANCELLED, 5, "migration-operator", "corr-cancel");

        assertThat(ciBlocked.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(ciCancelled.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(manualBlocked.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(manualCancelled.status()).isEqualTo(TaskStatus.CANCELLED);
    }

    private void saveLegacyTask(String taskId, TaskType taskType, TaskStatus status, long version) {
        tasks.save(new WorkflowTask(taskId, taskType, status,
                new WorkflowScope("DEMO-" + taskId, "REPO_A", "legacy-ref"),
                "legacy-" + taskId + "-" + taskType, "developer-1", null, version, NOW, NOW));
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

    private static final class AppendThenFailAuditRepository implements AuditEventRepository {
        private final InMemoryAuditEventRepository delegate = new InMemoryAuditEventRepository();
        private boolean failAfterAppend;

        @Override
        public AuditEvent append(AuditEvent event) {
            AuditEvent appended = delegate.append(event);
            if (failAfterAppend) throw new IllegalStateException("audit persistence failed after append");
            return appended;
        }

        @Override
        public void delete(String eventId) { delegate.delete(eventId); }

        @Override
        public java.util.List<AuditEvent> findByTaskId(String taskId) {
            return delegate.findByTaskId(taskId);
        }
    }
}
