package dev.sdlc.workflow.task;

public final class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String taskId) {
        super("Task was not found: " + taskId);
    }
}
