package dev.sdlc.workflow.journey;

import java.util.List;

public record JourneyManifest(
        String schemaVersion,
        String journeyId,
        String domainId,
        int version,
        List<JourneyRepositoryEntry> repositories,
        List<JourneyScreen> screens,
        List<JourneyHttpEdge> httpEdges,
        JourneyReleasePolicy releasePolicy,
        JourneyFeatureFlag featureFlag,
        List<JourneyE2EOwner> e2eOwners) {
    public JourneyManifest {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
        screens = screens == null ? List.of() : List.copyOf(screens);
        httpEdges = httpEdges == null ? List.of() : List.copyOf(httpEdges);
        e2eOwners = e2eOwners == null ? List.of() : List.copyOf(e2eOwners);
    }
}
