package dev.sdlc.workflow.enterprise;

@FunctionalInterface
public interface EnterpriseCancellation {
    EnterpriseCancellation NEVER = () -> false;
    boolean cancelled();
}
