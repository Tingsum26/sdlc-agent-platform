package dev.sdlc.workflow.artifact;

import java.util.Optional;

public interface ArtifactStore {
    ArtifactMetadata save(ArtifactMetadata artifact);

    void delete(String artifactId, int version);

    Optional<ArtifactMetadata> find(String artifactId, int version);

    Optional<ArtifactMetadata> findLatest(String artifactId);
}
