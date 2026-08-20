package dev.sdlc.workflow.repotask;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Instant;
import java.util.Objects;

public record RepoTask(
        String repoTaskId,
        String ticketId,
        String repositoryAlias,
        String baseCommit,
        RepoTaskStatus status,
        EvidenceClassification evidenceClassification,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public RepoTask {
        Objects.requireNonNull(repoTaskId, "repoTaskId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(repositoryAlias, "repositoryAlias");
        Objects.requireNonNull(baseCommit, "baseCommit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evidenceClassification, "evidenceClassification");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepoTask(String repoTaskId, String ticketId, String repositoryAlias, String baseCommit,
            RepoTaskStatus status, long version, Instant createdAt, Instant updatedAt) {
        this(repoTaskId, ticketId, repositoryAlias, baseCommit, status, EvidenceClassification.REAL,
                version, createdAt, updatedAt);
    }

    RepoTask transitionedTo(RepoTaskStatus target, Instant now) {
        return new RepoTask(repoTaskId, ticketId, repositoryAlias, baseCommit, target, evidenceClassification,
                version + 1, createdAt, now);
    }
}
