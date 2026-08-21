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
        return persistWithAudit(null, task, actorId, "TASK_CREATED", null, task.status(), correlationId,
                null, null).task();
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
                task.status(), claimed.status(), correlationId, null, null).task();
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
                task.status(), skipped.status(), correlationId, null, null).task();
    }

    public synchronized WorkflowTask transition(
            String taskId,
            TaskStatus expectedStatus,
            TaskStatus targetStatus,
            long expectedVersion,
            String actorId,
            String correlationId) {
        return transitionCommit(taskId, expectedStatus, targetStatus, expectedVersion, actorId, correlationId,
                null, null).task();
    }

    private WorkflowTaskCommit transitionCommit(
            String taskId,
            TaskStatus expectedStatus,
            TaskStatus targetStatus,
            long expectedVersion,
            String actorId,
            String correlationId,
            String relatedArtifactId,
            Integer relatedArtifactVersion) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        if (task.status() != expectedStatus) {
            throw new IllegalTaskTransitionException(
                    "Expected status " + expectedStatus + " but was " + task.status());
        }
        transitionPolicy.requireAllowed(task.type(), task.status(), targetStatus);
        WorkflowTask changed = task.transitionedTo(targetStatus, clock.instant());
        return persistWithAudit(task, changed, actorId, "TASK_TRANSITIONED",
                task.status(), changed.status(), correlationId, relatedArtifactId, relatedArtifactVersion);
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

    public synchronized WorkflowTaskCommit transitionAfterApprovalCommit(
            String taskId,
            long expectedVersion,
            String actorId,
            String correlationId,
            String artifactId,
            int artifactVersion) {
        if (artifactId == null || artifactId.isBlank() || artifactVersion < 1) {
            throw new IllegalArgumentException("Approval commit requires an artifact identity");
        }
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        return transitionCommit(taskId, TaskStatus.WAITING_FOR_APPROVAL,
                transitionPolicy.targetAfterApproval(task.type()), expectedVersion, actorId, correlationId,
                artifactId, artifactVersion);
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
                task.status(), changed.status(), correlationId, null, null).task();
    }

    public synchronized int releaseExpiredLeases(Instant now, String actorId, String correlationId) {
        int released = 0;
        for (WorkflowTask task : tasks.findAll()) {
            if (task.status() == TaskStatus.LOCAL_COPILOT_RUNNING
                    && task.leaseExpiresAt() != null
                    && task.leaseExpiresAt().isBefore(now)) {
                WorkflowTask returned = task.released(now);
                persistWithAudit(task, returned, actorId, "LEASE_EXPIRED",
                        task.status(), returned.status(), correlationId, null, null);
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

    public synchronized void compensateCommittedTransition(WorkflowTask previous, WorkflowTaskCommit commit) {
        WorkflowTask committed = commit.task();
        WorkflowTask current = requireVersion(committed.taskId(), committed.version());
        if (!current.equals(committed) || !previous.taskId().equals(committed.taskId())) {
            throw new StaleTaskVersionException("Workflow task changed before compensation");
        }
        AuditEvent event = commit.auditEvent();
        boolean exactEventExists = auditEvents.findByTaskId(committed.taskId()).stream()
                .anyMatch(candidate -> candidate.equals(event));
        if (!exactEventExists) {
            throw new IllegalStateException("Committed task transition has no exact audit event");
        }
        AuditRecovery auditRecovery = invalidateAndDeleteAuditEvent(event);
        RuntimeException recoveryFailure = auditRecovery.failure();
        if (auditRecovery.safeToRestoreTask()) {
            try {
                tasks.restore(previous, committed.version());
            } catch (RuntimeException exception) {
                if (recoveryFailure == null) recoveryFailure = exception;
                else recoveryFailure.addSuppressed(exception);
            }
        } else {
            IllegalStateException unsafeRestore = new IllegalStateException(
                    "Task was not restored because the approval event could not be invalidated or deleted");
            if (recoveryFailure == null) recoveryFailure = unsafeRestore;
            else recoveryFailure.addSuppressed(unsafeRestore);
        }
        if (recoveryFailure != null) {
            throw new IllegalStateException("Workflow task compensation was incomplete", recoveryFailure);
        }
    }

    /**
     * Verifies the process-local approval commit fence used by Jira projection.
     * This is not a distributed transaction or a cross-process lock.
     */
    public synchronized WorkflowTask requireCommittedApproval(
            String taskId,
            Long approvedTaskVersion,
            String artifactId,
            int artifactVersion,
            String approvalCommitEventId) {
        if (approvedTaskVersion == null || approvalCommitEventId == null) {
            throw new IllegalStateException("Artifact approval is not bound to a task commit");
        }
        if (auditEvents.isInvalidated(approvalCommitEventId)) {
            throw new IllegalStateException("Artifact approval commit was invalidated");
        }
        WorkflowTask task = getTask(taskId);
        if (task.version() < approvedTaskVersion) {
            throw new IllegalStateException("Artifact approval task transition was compensated");
        }
        TaskStatus expectedTarget = transitionPolicy.targetAfterApproval(task.type());
        List<AuditEvent> taskAudit = auditEvents.findByTaskId(taskId);
        boolean committed = taskAudit.stream().anyMatch(event ->
                event.eventId().equals(approvalCommitEventId)
                        && event.action().equals("TASK_TRANSITIONED")
                        && event.taskVersion() == approvedTaskVersion
                        && event.previousStatus() == TaskStatus.WAITING_FOR_APPROVAL
                        && event.newStatus() == expectedTarget
                        && artifactId.equals(event.relatedArtifactId())
                        && Integer.valueOf(artifactVersion).equals(event.relatedArtifactVersion()));
        if (!committed) {
            throw new IllegalStateException("Artifact approval task transition is not committed");
        }
        if (!hasValidAuditChain(task, approvedTaskVersion, expectedTarget, taskAudit)) {
            throw new IllegalStateException("Current task state is not descended from the approval commit");
        }
        return task;
    }

    private boolean hasValidAuditChain(
            WorkflowTask task,
            long approvedTaskVersion,
            TaskStatus approvalTarget,
            List<AuditEvent> taskAudit) {
        long expectedVersion = approvedTaskVersion;
        TaskStatus expectedStatus = approvalTarget;
        if (task.version() == expectedVersion) return task.status() == expectedStatus;
        while (expectedVersion < task.version()) {
            long nextVersion = expectedVersion + 1;
            TaskStatus previousStatus = expectedStatus;
            AuditEvent next = taskAudit.stream()
                    .filter(event -> event.taskVersion() == nextVersion
                            && event.previousStatus() == previousStatus
                            && !auditEvents.isInvalidated(event.eventId()))
                    .findFirst()
                    .orElse(null);
            if (next == null) return false;
            expectedVersion = nextVersion;
            expectedStatus = next.newStatus();
        }
        return task.status() == expectedStatus;
    }

    private WorkflowTaskCommit persistWithAudit(
            WorkflowTask previous,
            WorkflowTask changed,
            String actorId,
            String action,
            TaskStatus previousStatus,
            TaskStatus nextStatus,
            String correlationId,
            String relatedArtifactId,
            Integer relatedArtifactVersion) {
        long sequence = auditEvents.findByTaskId(changed.taskId()).size() + 1L;
        AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), changed.taskId(), sequence, actorId,
                action, changed.evidenceClassification(), previousStatus, nextStatus,
                changed.version(), clock.instant(), correlationId, relatedArtifactId, relatedArtifactVersion);
        try {
            tasks.save(changed);
        } catch (RuntimeException failure) {
            compensateUncertainTaskSave(previous, changed, failure);
            throw failure;
        }
        try {
            auditEvents.append(event);
            return new WorkflowTaskCommit(changed, event);
        } catch (RuntimeException failure) {
            AuditRecovery auditRecovery = invalidateAndDeleteAuditEvent(event);
            RuntimeException recoveryFailure = auditRecovery.failure();
            if (auditRecovery.safeToRestoreTask()) {
                try {
                    compensateUncertainTaskSave(previous, changed, failure);
                } catch (RuntimeException exception) {
                    if (recoveryFailure == null) recoveryFailure = exception;
                    else recoveryFailure.addSuppressed(exception);
                }
            } else {
                IllegalStateException unsafeRestore = new IllegalStateException(
                        "Task was not restored because the audit event could not be invalidated or deleted");
                if (recoveryFailure == null) recoveryFailure = unsafeRestore;
                else recoveryFailure.addSuppressed(unsafeRestore);
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

    private AuditRecovery invalidateAndDeleteAuditEvent(AuditEvent event) {
        RuntimeException recoveryFailure = null;
        boolean invalidated = false;
        try {
            auditEvents.invalidate(event.eventId());
            invalidated = true;
        } catch (RuntimeException exception) {
            recoveryFailure = exception;
        }
        boolean deleted = false;
        try {
            auditEvents.delete(event.eventId());
            deleted = true;
        } catch (RuntimeException exception) {
            if (recoveryFailure == null) recoveryFailure = exception;
            else recoveryFailure.addSuppressed(exception);
        }
        return new AuditRecovery(invalidated || deleted, recoveryFailure);
    }

    private record AuditRecovery(boolean safeToRestoreTask, RuntimeException failure) {
    }

    /**
     * Process-local uncertain-write recovery. The repository's conditional
     * restore/delete prevents overwriting a different version, but this is not
     * a distributed transaction or cross-instance lock.
     */
    private void compensateUncertainTaskSave(
            WorkflowTask previous,
            WorkflowTask attempted,
            RuntimeException originalFailure) {
        try {
            WorkflowTask current = tasks.findById(attempted.taskId()).orElse(null);
            if (previous == null && current == null || previous != null && previous.equals(current)) {
                return;
            }
            if (!attempted.equals(current)) {
                throw new StaleTaskVersionException(
                        "Workflow task changed before uncertain-save compensation");
            }
            if (previous == null) tasks.delete(attempted.taskId(), attempted.version());
            else tasks.restore(previous, attempted.version());
        } catch (RuntimeException recoveryFailure) {
            IllegalStateException incomplete = new IllegalStateException(
                    "Workflow task save compensation was incomplete", originalFailure);
            incomplete.addSuppressed(recoveryFailure);
            throw incomplete;
        }
    }
}
