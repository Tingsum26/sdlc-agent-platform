package dev.sdlc.workflow.task;

public final class IllegalTaskTransitionException extends RuntimeException {
    public IllegalTaskTransitionException(String message) {
        super(message);
    }
}
