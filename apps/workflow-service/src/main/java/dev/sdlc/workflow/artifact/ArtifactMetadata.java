package dev.sdlc.workflow.artifact;

import java.time.Instant;
import java.util.List;

public record ArtifactMetadata(
        String artifactId,
        String taskId,
        ArtifactType type,
        int version,
        String contentHash,
        List<ArtifactSection> sections,
        String createdBy,
        Instant createdAt,
        String approvedBy,
        Instant approvedAt) {

    public boolean approved() {
        return approvedAt != null;
    }

    public ArtifactMetadata approvedBy(String actorId, Instant at) {
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, actorId, at);
    }
}
