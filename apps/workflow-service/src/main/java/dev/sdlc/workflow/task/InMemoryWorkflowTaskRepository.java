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

    @Override
    public synchronized void restore(WorkflowTask task, long expectedCurrentVersion) {
        WorkflowTask current = tasks.get(task.taskId());
        if (current == null || current.version() != expectedCurrentVersion) {
            throw new StaleTaskVersionException("Workflow task changed before compensation");
        }
        tasks.put(task.taskId(), task);
    }

    @Override
    public synchronized void delete(String taskId, long expectedVersion) {
        WorkflowTask current = tasks.get(taskId);
        if (current == null) return;
        if (current.version() != expectedVersion) {
            throw new StaleTaskVersionException("Workflow task changed before compensation");
        }
        tasks.remove(taskId);
    }
}
