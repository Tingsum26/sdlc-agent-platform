package dev.sdlc.workflow.task;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WorkflowTaskService {

    private final WorkflowTaskRepository tasks;
    private final AuditEventRepository auditEvents;
    private final TaskTransitionPolicy transitionPolicy;
    private final Clock clock;

    public WorkflowTaskService(
            WorkflowTaskRepository tasks,
            AuditEventRepository auditEvents,
            TaskTransitionPolicy transitionPolicy,
            Clock clock) {
        this.tasks = Objects.requireNonNull(tasks);
        this.auditEvents = Objects.requireNonNull(auditEvents);
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized WorkflowTask createTask(
            String taskId,
            TaskType type,
            WorkflowScope scope,
            String idempotencyKey,
            String actorId,
            String correlationId) {
        return createTask(taskId, type, scope, idempotencyKey, null, actorId, correlationId);
    }

    public synchronized WorkflowTask createTask(
            String taskId,
            TaskType type,
            WorkflowScope scope,
            String idempotencyKey,
            String compatibleLegacyKey,
            String actorId,
            String correlationId) {
        return createTask(taskId, type, scope, idempotencyKey, compatibleLegacyKey,
                EvidenceClassification.REAL, actorId, correlationId);
    }

    public synchronized WorkflowTask createTask(
            String taskId,
            TaskType type,
            WorkflowScope scope,
            String idempotencyKey,
            String compatibleLegacyKey,
            EvidenceClassification evidenceClassification,
            String actorId,
            String correlationId) {
        List<String> compatibleLegacyKeys = compatibleLegacyKey == null
                ? List.of() : List.of(compatibleLegacyKey);
        return createTaskWithLegacyKeys(taskId, type, scope, idempotencyKey, compatibleLegacyKeys,
                evidenceClassification, actorId, correlationId);
    }

    public synchronized WorkflowTask createTaskWithLegacyKeys(
            String taskId,
            TaskType type,
            WorkflowScope scope,
            String idempotencyKey,
            List<String> compatibleLegacyKeys,
            EvidenceClassification evidenceClassification,
            String actorId,
            String correlationId) {
        WorkflowTask existing = tasks.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null && !sameTaskIdentity(existing, type, scope, evidenceClassification)) {
            throw new IllegalArgumentException("Idempotency key belongs to a different task identity");
        }
        if (existing == null) {
            for (String compatibleLegacyKey : compatibleLegacyKeys) {
                existing = tasks.findByIdempotencyKey(compatibleLegacyKey)
                        .filter(task -> sameTaskIdentity(task, type, scope, evidenceClassification))
                        .orElse(null);
                if (existing != null) break;
            }
        }
        if (existing != null) return existing;
        Instant now = clock.instant();
        WorkflowTask task = new WorkflowTask(taskId, type, TaskStatus.WAITING_FOR_LOCAL_COPILOT,
                evidenceClassification, scope, idempotencyKey, null, null, 0, now, now);
        return persistWithAudit(null, task, actorId, "TASK_CREATED", null, task.status(), correlationId);
    }

    private static boolean sameTaskIdentity(WorkflowTask task, TaskType type, WorkflowScope scope,
            EvidenceClassification evidenceClassification) {
        return task.type() == type
                && task.scope().equals(scope)
                && task.evidenceClassification() == evidenceClassification;
    }

    public synchronized WorkflowTask claimTask(
            String taskId,
            String actorId,
            Duration lease,
            long expectedVersion,
            String correlationId) {
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Lease must be positive");
        }
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        transitionPolicy.requireAllowed(task.type(), task.status(), TaskStatus.LOCAL_COPILOT_RUNNING);
        Instant now = clock.instant();
        WorkflowTask claimed = task.claimedBy(actorId, now.plus(lease), now);
        return persistWithAudit(task, claimed, actorId, "TASK_CLAIMED",
                task.status(), claimed.status(), correlationId);
    }

    public synchronized WorkflowTask skipTask(
            String taskId,
            long expectedVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        if (task.status() != TaskStatus.WAITING_FOR_LOCAL_COPILOT
                && task.status() != TaskStatus.LOCAL_COPILOT_RUNNING
                && task.status() != TaskStatus.WAITING_FOR_USER_CONFIRMATION) {
            throw new IllegalTaskTransitionException("Stage cannot be skipped from " + task.status());
        }
        WorkflowTask skipped = task.transitionedTo(TaskStatus.COMPLETED, clock.instant());
        return persistWithAudit(task, skipped, actorId, "TASK_SKIPPED",
                task.status(), skipped.status(), correlationId);
    }

    public synchronized WorkflowTask transition(
            String taskId,
            TaskStatus expectedStatus,
            TaskStatus targetStatus,
            long expectedVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        if (task.status() != expectedStatus) {
            throw new IllegalTaskTransitionException("Expected status " + expectedStatus + " but was " + task.status());
        }
        transitionPolicy.requireAllowed(task.type(), task.status(), targetStatus);
        WorkflowTask changed = task.transitionedTo(targetStatus, clock.instant());
        return persistWithAudit(task, changed, actorId, "TASK_TRANSITIONED",
                task.status(), changed.status(), correlationId);
    }

    public synchronized WorkflowTask validateTransition(
            String taskId,
            TaskStatus expectedStatus,
            TaskStatus targetStatus,
            long expectedVersion) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        if (task.status() != expectedStatus) {
            throw new IllegalTaskTransitionException("Expected status " + expectedStatus + " but was " + task.status());
        }
        transitionPolicy.requireAllowed(task.type(), task.status(), targetStatus);
        return task;
    }

    public synchronized WorkflowTask validateTransitionAfterApproval(String taskId, long expectedVersion) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        return validateTransition(taskId, TaskStatus.WAITING_FOR_APPROVAL,
                transitionPolicy.targetAfterApproval(task.type()), expectedVersion);
    }

    public synchronized WorkflowTask transitionAfterApproval(
            String taskId,
            long expectedVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        return transition(taskId, TaskStatus.WAITING_FOR_APPROVAL,
                transitionPolicy.targetAfterApproval(task.type()), expectedVersion, actorId, correlationId);
    }

    public synchronized WorkflowTask transitionAfterPassedCi(
            String taskId,
            long expectedVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        return transition(taskId, TaskStatus.WAITING_FOR_CI,
                transitionPolicy.targetAfterPassedCi(task.type()), expectedVersion, actorId, correlationId);
    }

    public synchronized WorkflowTask completeLegacyApprovalOnlyTask(
            String taskId,
            long expectedVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        if (!transitionPolicy.isApprovalOnly(task.type())
                || task.status() != TaskStatus.WAITING_FOR_MANUAL_E2E) {
            throw new IllegalTaskTransitionException(
                    "Compatibility completion is not allowed for " + task.type() + " in " + task.status());
        }
        WorkflowTask changed = task.transitionedTo(TaskStatus.COMPLETED, clock.instant());
        return persistWithAudit(task, changed, actorId, "LEGACY_STAGE_COMPLETED",
                task.status(), changed.status(), correlationId);
    }

    public synchronized int releaseExpiredLeases(Instant now, String actorId, String correlationId) {
        int released = 0;
        for (WorkflowTask task : tasks.findAll()) {
            if (task.status() == TaskStatus.LOCAL_COPILOT_RUNNING
                    && task.leaseExpiresAt() != null
                    && task.leaseExpiresAt().isBefore(now)) {
                WorkflowTask returned = task.released(now);
                persistWithAudit(task, returned, actorId, "LEASE_EXPIRED",
                        task.status(), returned.status(), correlationId);
                released++;
            }
        }
        return released;
    }

    public synchronized WorkflowTask getTask(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    public synchronized List<WorkflowTask> listTasks() {
        return tasks.findAll();
    }

    public synchronized List<AuditEvent> listAuditEvents(String taskId) {
        getTask(taskId);
        return auditEvents.findByTaskId(taskId);
    }

    private WorkflowTask requireVersion(String taskId, long expectedVersion) {
        WorkflowTask task = tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        if (task.version() != expectedVersion) {
            throw new StaleTaskVersionException(
                    "Expected task version " + expectedVersion + " but was " + task.version());
        }
        return task;
    }

    public synchronized void compensateCommittedTransition(WorkflowTask previous, WorkflowTask committed) {
        WorkflowTask current = requireVersion(committed.taskId(), committed.version());
        if (!current.equals(committed) || !previous.taskId().equals(committed.taskId())) {
            throw new StaleTaskVersionException("Workflow task changed before compensation");
        }
        AuditEvent event = auditEvents.findByTaskId(committed.taskId()).stream()
                .filter(candidate -> candidate.taskVersion() == committed.version())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("Committed task transition has no audit event"));
        RuntimeException recoveryFailure = null;
        try {
            auditEvents.delete(event.eventId());
        } catch (RuntimeException exception) {
            recoveryFailure = exception;
        }
        try {
            tasks.restore(previous, committed.version());
        } catch (RuntimeException exception) {
            if (recoveryFailure == null) recoveryFailure = exception;
            else recoveryFailure.addSuppressed(exception);
        }
        if (recoveryFailure != null) {
            throw new IllegalStateException("Workflow task compensation was incomplete", recoveryFailure);
        }
    }

    /**
     * Verifies the process-local approval commit fence used by Jira projection.
     * This is not a distributed transaction or a cross-process lock.
     */
    public synchronized WorkflowTask requireCommittedApproval(String taskId, Long approvedTaskVersion) {
        if (approvedTaskVersion == null) {
            throw new IllegalStateException("Artifact approval is not bound to a task commit");
        }
        WorkflowTask task = getTask(taskId);
        if (task.version() < approvedTaskVersion) {
            throw new IllegalStateException("Artifact approval task transition was compensated");
        }
        TaskStatus expectedTarget = transitionPolicy.targetAfterApproval(task.type());
        boolean committed = auditEvents.findByTaskId(taskId).stream().anyMatch(event ->
                event.taskVersion() == approvedTaskVersion
                        && event.previousStatus() == TaskStatus.WAITING_FOR_APPROVAL
                        && event.newStatus() == expectedTarget);
        if (!committed) {
            throw new IllegalStateException("Artifact approval task transition is not committed");
        }
        return task;
    }

    private WorkflowTask persistWithAudit(
            WorkflowTask previous,
            WorkflowTask changed,
            String actorId,
            String action,
            TaskStatus previousStatus,
            TaskStatus nextStatus,
            String correlationId) {
        long sequence = auditEvents.findByTaskId(changed.taskId()).size() + 1L;
        AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), changed.taskId(), sequence, actorId,
                action, changed.evidenceClassification(), previousStatus, nextStatus,
                changed.version(), clock.instant(), correlationId);
        tasks.save(changed);
        try {
            auditEvents.append(event);
            return changed;
        } catch (RuntimeException failure) {
            RuntimeException recoveryFailure = null;
            try {
                auditEvents.delete(event.eventId());
            } catch (RuntimeException exception) {
                recoveryFailure = exception;
            }
            try {
                if (previous == null) tasks.delete(changed.taskId(), changed.version());
                else tasks.restore(previous, changed.version());
            } catch (RuntimeException exception) {
                if (recoveryFailure == null) recoveryFailure = exception;
                else recoveryFailure.addSuppressed(exception);
            }
            if (recoveryFailure != null) {
                IllegalStateException incomplete = new IllegalStateException(
                        "Workflow task and audit compensation was incomplete", failure);
                incomplete.addSuppressed(recoveryFailure);
                throw incomplete;
            }
            throw failure;
        }
    }
}
