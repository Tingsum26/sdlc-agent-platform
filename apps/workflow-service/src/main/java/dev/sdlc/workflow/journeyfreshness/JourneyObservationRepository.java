package dev.sdlc.workflow.journeyfreshness;

import java.util.List;
import java.util.Optional;

public interface JourneyObservationRepository {
    Optional<JourneyObservation> find(String journeyId, String repositoryAlias);
    JourneyObservation save(JourneyObservation observation);
    List<JourneyObservation> findAll();
}
