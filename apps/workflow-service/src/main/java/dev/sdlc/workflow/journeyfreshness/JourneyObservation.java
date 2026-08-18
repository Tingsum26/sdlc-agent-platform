package dev.sdlc.workflow.journeyfreshness;

import java.time.Instant;
import java.util.Objects;

public record JourneyObservation(
        String journeyId,
        String repositoryAlias,
        String commit,
        Instant observedAt,
        boolean staleMarked) {

    public JourneyObservation {
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(repositoryAlias, "repositoryAlias");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
