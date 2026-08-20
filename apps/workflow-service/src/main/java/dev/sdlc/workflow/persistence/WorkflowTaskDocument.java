package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("workflowTasks")
public record WorkflowTaskDocument(
        @Id String taskId, TaskType type, TaskStatus status, EvidenceClassification evidenceClassification,
        WorkflowScope scope, String idempotencyKey,
        String assigneeId, Instant leaseExpiresAt, long version, Instant createdAt, Instant updatedAt) {
    public static WorkflowTaskDocument fromDomain(WorkflowTask task) {
        return new WorkflowTaskDocument(task.taskId(), task.type(), task.status(), task.evidenceClassification(),
                task.scope(), task.idempotencyKey(),
                task.assigneeId(), task.leaseExpiresAt(), task.version(), task.createdAt(), task.updatedAt());
    }

    public WorkflowTask toDomain() {
        return new WorkflowTask(taskId, type, status,
                evidenceClassification == null ? EvidenceClassification.REAL : evidenceClassification,
                scope, idempotencyKey, assigneeId, leaseExpiresAt,
                version, createdAt, updatedAt);
    }
}
