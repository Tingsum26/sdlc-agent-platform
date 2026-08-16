package dev.sdlc.workflow.task;

import java.util.List;
import java.util.Optional;

public interface WorkflowTaskRepository {
    Optional<WorkflowTask> findById(String taskId);

    Optional<WorkflowTask> findByIdempotencyKey(String idempotencyKey);

    List<WorkflowTask> findAll();

    WorkflowTask save(WorkflowTask task);
}
