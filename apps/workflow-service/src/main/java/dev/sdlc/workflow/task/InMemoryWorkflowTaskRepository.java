package dev.sdlc.workflow.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryWorkflowTaskRepository implements WorkflowTaskRepository {

    private final Map<String, WorkflowTask> tasks = new LinkedHashMap<>();

    @Override
    public synchronized Optional<WorkflowTask> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public synchronized Optional<WorkflowTask> findByIdempotencyKey(String idempotencyKey) {
        return tasks.values().stream().filter(task -> task.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override
    public synchronized List<WorkflowTask> findAll() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public synchronized WorkflowTask save(WorkflowTask task) {
        tasks.put(task.taskId(), task);
        return task;
    }
}
