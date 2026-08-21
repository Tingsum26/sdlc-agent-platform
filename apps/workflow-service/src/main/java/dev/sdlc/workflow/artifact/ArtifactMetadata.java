package dev.sdlc.workflow.artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
        Instant approvedAt,
        @JsonIgnore ArtifactApprovalStatus approvalStatus,
        @JsonIgnore Long approvedTaskVersion) {

    public ArtifactMetadata {
        if (approvalStatus == null) {
            approvalStatus = approvedAt == null ? ArtifactApprovalStatus.DRAFT : ArtifactApprovalStatus.APPROVED;
        }
    }

    public ArtifactMetadata(
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
        this(artifactId, taskId, type, version, contentHash, sections, createdBy, createdAt,
                approvedBy, approvedAt,
                approvedAt == null ? ArtifactApprovalStatus.DRAFT : ArtifactApprovalStatus.APPROVED, null);
    }

    public boolean approved() {
        return approvalStatus == ArtifactApprovalStatus.APPROVED;
    }

    public ArtifactMetadata approvedBy(String actorId, Instant at) {
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, actorId, at, ArtifactApprovalStatus.APPROVED, null);
    }

    public ArtifactMetadata pendingApprovalBy(String actorId) {
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, actorId, null, ArtifactApprovalStatus.PENDING, null);
    }

    public ArtifactMetadata commitApproval(Instant at, Long taskVersion) {
        if (approvalStatus != ArtifactApprovalStatus.PENDING) {
            throw new IllegalStateException("Only a pending artifact approval can be committed");
        }
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, approvedBy, at, ArtifactApprovalStatus.APPROVED, taskVersion);
    }
}
