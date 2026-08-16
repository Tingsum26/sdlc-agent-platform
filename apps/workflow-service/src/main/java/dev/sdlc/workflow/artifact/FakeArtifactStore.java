package dev.sdlc.workflow.artifact;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FakeArtifactStore implements ArtifactStore {

    private final Map<String, ArtifactMetadata> artifacts = new LinkedHashMap<>();

    @Override
    public synchronized ArtifactMetadata save(ArtifactMetadata artifact) {
        artifacts.put(key(artifact.artifactId(), artifact.version()), artifact);
        return artifact;
    }

    @Override
    public synchronized Optional<ArtifactMetadata> find(String artifactId, int version) {
        return Optional.ofNullable(artifacts.get(key(artifactId, version)));
    }

    @Override
    public synchronized Optional<ArtifactMetadata> findLatest(String artifactId) {
        return artifacts.values().stream()
                .filter(artifact -> artifact.artifactId().equals(artifactId))
                .max(Comparator.comparingInt(ArtifactMetadata::version));
    }

    private String key(String artifactId, int version) {
        return artifactId + ":" + version;
    }
}
