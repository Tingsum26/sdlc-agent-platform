package dev.sdlc.workflow.repotask;

import java.time.Instant;
import java.util.Objects;

public record RepoTask(
        String repoTaskId,
        String ticketId,
        String repositoryAlias,
        String baseCommit,
        RepoTaskStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public RepoTask {
        Objects.requireNonNull(repoTaskId, "repoTaskId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(repositoryAlias, "repositoryAlias");
        Objects.requireNonNull(baseCommit, "baseCommit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    RepoTask transitionedTo(RepoTaskStatus target, Instant now) {
        return new RepoTask(repoTaskId, ticketId, repositoryAlias, baseCommit, target, version + 1, createdAt, now);
    }
}
