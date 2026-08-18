package dev.sdlc.workflow.journeyfreshness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryJourneyObservationRepository implements JourneyObservationRepository {
    private final ConcurrentMap<String, JourneyObservation> observations = new ConcurrentHashMap<>();

    @Override
    public Optional<JourneyObservation> find(String journeyId, String repositoryAlias) {
        return Optional.ofNullable(observations.get(key(journeyId, repositoryAlias)));
    }

    @Override
    public JourneyObservation save(JourneyObservation observation) {
        observations.put(key(observation.journeyId(), observation.repositoryAlias()), observation);
        return observation;
    }

    @Override
    public List<JourneyObservation> findAll() {
        return new ArrayList<>(observations.values());
    }

    private static String key(String journeyId, String repositoryAlias) {
        return journeyId + ":" + repositoryAlias;
    }
}
