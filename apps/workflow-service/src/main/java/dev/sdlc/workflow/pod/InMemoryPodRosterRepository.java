package dev.sdlc.workflow.pod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryPodRosterRepository implements PodRosterRepository {
    private final Map<String, PodRoster> rosters = new HashMap<>();

    @Override
    public synchronized Optional<PodRoster> find(String journeyId) {
        return Optional.ofNullable(rosters.get(journeyId));
    }

    @Override
    public synchronized PodRoster save(PodRoster roster, long expectedRevision) {
        long current = find(roster.journeyId()).map(PodRoster::revision).orElse(0L);
        if (current != expectedRevision || roster.revision() != expectedRevision + 1) {
            throw new StaleRosterRevisionException("Pod roster revision changed");
        }
        rosters.put(roster.journeyId(), roster);
        return roster;
    }
}
