package dev.sdlc.workflow.pod;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record PodMembership(
        String membershipId,
        String employeeId,
        String principalId,
        String displayLabel,
        String role,
        String journeyId,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<String> aliases) {

    public PodMembership {
        Objects.requireNonNull(membershipId, "membershipId");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(displayLabel, "displayLabel");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must not precede effectiveFrom");
        }
    }

    public boolean activeOn(LocalDate date) {
        return active && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
