package dev.sdlc.workflow.pod;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PodRoster(String journeyId, long revision, List<PodMembership> memberships, Instant updatedAt) {
    public PodRoster {
        Objects.requireNonNull(journeyId, "journeyId");
        memberships = List.copyOf(memberships);
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
