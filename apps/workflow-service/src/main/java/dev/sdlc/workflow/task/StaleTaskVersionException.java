package dev.sdlc.workflow.task;

public final class StaleTaskVersionException extends RuntimeException {
    public StaleTaskVersionException(String message) {
        super(message);
    }
}
