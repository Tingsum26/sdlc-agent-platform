package dev.sdlc.workflow.identity;

import java.time.Instant;

public record DirectoryPerson(
        String principalId,
        String employeeId,
        String displayLabel,
        OnboardingStatus onboardingStatus,
        Instant updatedAt) {
}
