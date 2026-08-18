package dev.sdlc.workflow.conflict;

/**
 * The requested mutation conflicts with the current aggregate state
 * (stale version, disallowed transition, unresolved dependency, or
 * an already-completed approval). Maps to HTTP 409 Conflict.
 */
public final class WorkflowConflictException extends RuntimeException {

    public WorkflowConflictException(String message) {
        super(message);
    }
}
