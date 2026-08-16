package dev.sdlc.workflow.pod;

public final class StaleRosterRevisionException extends RuntimeException {
    public StaleRosterRevisionException(String message) {
        super(message);
    }
}
