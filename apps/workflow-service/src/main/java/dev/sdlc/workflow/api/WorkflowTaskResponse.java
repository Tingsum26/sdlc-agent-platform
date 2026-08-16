package dev.sdlc.workflow.api;

import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import java.time.Instant;

public record WorkflowTaskResponse(
        String schemaVersion,
        String taskId,
        TaskType type,
        TaskStatus status,
        WorkflowScope scope,
        String assigneeId,
        Instant leaseExpiresAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static WorkflowTaskResponse from(WorkflowTask task) {
        return new WorkflowTaskResponse("1.0", task.taskId(), task.type(), task.status(), task.scope(),
                task.assigneeId(), task.leaseExpiresAt(), task.version(), task.createdAt(), task.updatedAt());
    }
}
