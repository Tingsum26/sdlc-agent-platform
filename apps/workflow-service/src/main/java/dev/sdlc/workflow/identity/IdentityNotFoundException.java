package dev.sdlc.workflow.identity;

public final class IdentityNotFoundException extends RuntimeException {
    public IdentityNotFoundException() {
        super("Enterprise principal is not bound");
    }
}
