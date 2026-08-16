package dev.sdlc.workflow.journey;

public record JourneyReleasePolicy(
        boolean webApiFirst,
        String nativeReleaseTrain,
        int compatibilityWindowDays,
        String rollbackRule) { }
