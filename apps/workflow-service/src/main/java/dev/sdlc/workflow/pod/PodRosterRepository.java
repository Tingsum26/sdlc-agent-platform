package dev.sdlc.workflow.pod;

import java.util.Optional;

public interface PodRosterRepository {
    Optional<PodRoster> find(String journeyId);

    PodRoster save(PodRoster roster, long expectedRevision);
}
