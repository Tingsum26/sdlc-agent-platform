package dev.sdlc.workflow.artifact;

import java.util.Optional;

public interface ArtifactStore {
    ArtifactMetadata save(ArtifactMetadata artifact);

    Optional<ArtifactMetadata> find(String artifactId, int version);

    Optional<ArtifactMetadata> findLatest(String artifactId);
}
