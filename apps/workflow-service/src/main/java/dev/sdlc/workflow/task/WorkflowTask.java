package dev.sdlc.workflow.task;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Instant;
import java.util.Objects;

public record WorkflowTask(
        String taskId,
        TaskType type,
        TaskStatus status,
        EvidenceClassification evidenceClassification,
        WorkflowScope scope,
        String idempotencyKey,
        String assigneeId,
        Instant leaseExpiresAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public WorkflowTask {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evidenceClassification, "evidenceClassification");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public WorkflowTask(String taskId, TaskType type, TaskStatus status, WorkflowScope scope,
            String idempotencyKey, String assigneeId, Instant leaseExpiresAt, long version,
            Instant createdAt, Instant updatedAt) {
        this(taskId, type, status, EvidenceClassification.REAL, scope, idempotencyKey, assigneeId,
                leaseExpiresAt, version, createdAt, updatedAt);
    }

    WorkflowTask claimedBy(String actorId, Instant leaseEnd, Instant now) {
        return new WorkflowTask(taskId, type, TaskStatus.LOCAL_COPILOT_RUNNING, evidenceClassification, scope, idempotencyKey,
                actorId, leaseEnd, version + 1, createdAt, now);
    }

    WorkflowTask transitionedTo(TaskStatus target, Instant now) {
        return new WorkflowTask(taskId, type, target, evidenceClassification, scope, idempotencyKey, assigneeId,
                target == TaskStatus.LOCAL_COPILOT_RUNNING ? leaseExpiresAt : null,
                version + 1, createdAt, now);
    }

    WorkflowTask released(Instant now) {
        return new WorkflowTask(taskId, type, TaskStatus.WAITING_FOR_LOCAL_COPILOT, evidenceClassification, scope, idempotencyKey,
                null, null, version + 1, createdAt, now);
    }
}
