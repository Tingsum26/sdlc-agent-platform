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
        @JsonIgnore Long approvedTaskVersion,
        @JsonIgnore String approvalCommitEventId) {

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
                approvedAt == null ? ArtifactApprovalStatus.DRAFT : ArtifactApprovalStatus.APPROVED, null, null);
    }

    public boolean approved() {
        return approvalStatus == ArtifactApprovalStatus.APPROVED;
    }

    public ArtifactMetadata approvedBy(String actorId, Instant at) {
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, actorId, at, ArtifactApprovalStatus.APPROVED, null, null);
    }

    public ArtifactMetadata pendingApprovalBy(String actorId) {
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, actorId, null, ArtifactApprovalStatus.PENDING, null, null);
    }

    public ArtifactMetadata commitApproval(Instant at, Long taskVersion, String commitEventId) {
        if (approvalStatus != ArtifactApprovalStatus.PENDING) {
            throw new IllegalStateException("Only a pending artifact approval can be committed");
        }
        if (taskVersion != null && (commitEventId == null || commitEventId.isBlank())) {
            throw new IllegalArgumentException("Committed task approval requires an audit event ID");
        }
        return new ArtifactMetadata(artifactId, taskId, type, version, contentHash, sections,
                createdBy, createdAt, approvedBy, at, ArtifactApprovalStatus.APPROVED,
                taskVersion, commitEventId);
    }
}
