package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactApprovalStatus;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("artifacts")
public record ArtifactDocument(
        @Id String id, String artifactId, String taskId, ArtifactType type, int version, String contentHash,
        List<ArtifactSection> sections, String createdBy, Instant createdAt, String approvedBy, Instant approvedAt,
        ArtifactApprovalStatus approvalStatus, Long approvedTaskVersion) {
    public ArtifactDocument {
        sections = List.copyOf(sections);
    }

    public static ArtifactDocument fromDomain(ArtifactMetadata artifact) {
        return new ArtifactDocument(artifact.artifactId() + ":" + artifact.version(), artifact.artifactId(),
                artifact.taskId(), artifact.type(), artifact.version(), artifact.contentHash(), artifact.sections(),
                artifact.createdBy(), artifact.createdAt(), artifact.approvedBy(), artifact.approvedAt(),
                artifact.approvalStatus(), artifact.approvedTaskVersion());
    }

    public ArtifactMetadata toDomain() {
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, approvedBy, approvedAt,
                approvalStatus == null
                        ? (approvedAt == null ? ArtifactApprovalStatus.DRAFT : ArtifactApprovalStatus.APPROVED)
                        : approvalStatus,
                approvedTaskVersion);
    }
}
