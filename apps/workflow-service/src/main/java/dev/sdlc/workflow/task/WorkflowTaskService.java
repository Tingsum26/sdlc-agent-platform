package dev.sdlc.workflow.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

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
        WorkflowTask existing = tasks.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing == null && compatibleLegacyKey != null) {
            existing = tasks.findByIdempotencyKey(compatibleLegacyKey)
                    .filter(task -> task.type() == type
                            && task.scope().repositoryAlias().equals(scope.repositoryAlias()))
                    .orElse(null);
        }
        if (existing != null) return existing;
        Instant now = clock.instant();
        WorkflowTask task = new WorkflowTask(taskId, type, TaskStatus.WAITING_FOR_LOCAL_COPILOT,
                scope, idempotencyKey, null, null, 0, now, now);
        tasks.save(task);
        audit(task, actorId, "TASK_CREATED", null, task.status(), correlationId);
        return task;
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
        tasks.save(claimed);
        audit(claimed, actorId, "TASK_CLAIMED", task.status(), claimed.status(), correlationId);
        return claimed;
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
        tasks.save(skipped);
        audit(skipped, actorId, "TASK_SKIPPED", task.status(), skipped.status(), correlationId);
        return skipped;
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
        tasks.save(changed);
        audit(changed, actorId, "TASK_TRANSITIONED", task.status(), changed.status(), correlationId);
        return changed;
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

    public synchronized int releaseExpiredLeases(Instant now, String actorId, String correlationId) {
        int released = 0;
        for (WorkflowTask task : tasks.findAll()) {
            if (task.status() == TaskStatus.LOCAL_COPILOT_RUNNING
                    && task.leaseExpiresAt() != null
                    && task.leaseExpiresAt().isBefore(now)) {
                WorkflowTask returned = task.released(now);
                tasks.save(returned);
                audit(returned, actorId, "LEASE_EXPIRED", task.status(), returned.status(), correlationId);
                released++;
            }
        }
        return released;
    }

    public WorkflowTask getTask(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    public List<WorkflowTask> listTasks() {
        return tasks.findAll();
    }

    public List<AuditEvent> listAuditEvents(String taskId) {
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

    private void audit(
            WorkflowTask task,
            String actorId,
            String action,
            TaskStatus previous,
            TaskStatus next,
            String correlationId) {
        long sequence = auditEvents.findByTaskId(task.taskId()).size() + 1L;
        auditEvents.append(new AuditEvent(UUID.randomUUID().toString(), task.taskId(), sequence, actorId,
                action, previous, next, task.version(), clock.instant(), correlationId));
    }
}
