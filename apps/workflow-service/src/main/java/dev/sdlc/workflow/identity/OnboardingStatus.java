package dev.sdlc.workflow.identity;

public enum OnboardingStatus {
    /** Person is known to the roster but has never bound a workbench identity. */
    NOT_ONBOARDED,
    /** Person has bound a workbench identity (GitHub admin binding or enrollment code). */
    ONBOARDED
}
