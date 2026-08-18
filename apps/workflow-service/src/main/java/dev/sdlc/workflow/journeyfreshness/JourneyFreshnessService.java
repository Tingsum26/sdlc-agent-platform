package dev.sdlc.workflow.journeyfreshness;

import dev.sdlc.workflow.journey.JourneyManifest;
import dev.sdlc.workflow.journey.JourneyRepositoryEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JourneyFreshnessService {

    private static final Duration LIVE_WINDOW = Duration.ofHours(12);

    private final JourneyObservationRepository observations;
    private final Clock clock;

    public JourneyFreshnessService(JourneyObservationRepository observations, Clock clock) {
        this.observations = observations;
        this.clock = clock;
    }

    public JourneyObservation observe(String journeyId, String repositoryAlias, String commit) {
        return observe(journeyId, repositoryAlias, commit, clock.instant());
    }

    public JourneyObservation observe(String journeyId, String repositoryAlias, String commit, Instant observedAt) {
        if (journeyId == null || journeyId.isBlank()) throw new IllegalArgumentException("journeyId is required");
        if (repositoryAlias == null || repositoryAlias.isBlank()) {
            throw new IllegalArgumentException("repositoryAlias is required");
        }
        if (commit == null || commit.isBlank()) throw new IllegalArgumentException("commit is required");
        if (observedAt == null) throw new IllegalArgumentException("observedAt is required");
        JourneyObservation observation = new JourneyObservation(journeyId, repositoryAlias, commit, observedAt, false);
        return observations.save(observation);
    }

    public JourneyObservation markStale(String journeyId, String repositoryAlias) {
        return markStale(journeyId, repositoryAlias, clock.instant());
    }

    public JourneyObservation markStale(String journeyId, String repositoryAlias, Instant observedAt) {
        if (journeyId == null || journeyId.isBlank()) throw new IllegalArgumentException("journeyId is required");
        if (repositoryAlias == null || repositoryAlias.isBlank()) {
            throw new IllegalArgumentException("repositoryAlias is required");
        }
        if (observedAt == null) throw new IllegalArgumentException("observedAt is required");
        JourneyObservation existing = observations.find(journeyId, repositoryAlias)
                .orElseGet(() -> new JourneyObservation(journeyId, repositoryAlias, "", observedAt, false));
        return observations.save(new JourneyObservation(journeyId, repositoryAlias, existing.commit(),
                observedAt, true));
    }

    public Map<String, JourneyFreshness> freshnessFor(JourneyManifest manifest) {
        Map<String, JourneyFreshness> result = new LinkedHashMap<>();
        if (manifest == null) return result;
        Instant now = clock.instant();
        for (JourneyRepositoryEntry entry : manifest.repositories()) {
            result.put(entry.alias(), freshness(manifest.journeyId(), entry.alias(), entry.ref(), now));
        }
        return result;
    }

    private JourneyFreshness freshness(String journeyId, String repositoryAlias, String declaredRef, Instant now) {
        JourneyObservation observation = observations.find(journeyId, repositoryAlias).orElse(null);
        if (observation == null) return JourneyFreshness.OFFLINE;
        if (observation.staleMarked()) return JourneyFreshness.STALE;
        if (observation.observedAt().isBefore(now.minus(LIVE_WINDOW))) return JourneyFreshness.DELAYED;
        return observation.commit().equals(declaredRef) ? JourneyFreshness.LIVE : JourneyFreshness.STALE;
    }
}
