package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.JiraProjectionStatus;
import java.time.Instant;
import java.util.Objects;

public record JiraProjection(
        String projectionId,
        String ticketId,
        String milestoneId,
        String summary,
        JiraProjectionStatus status,
        int attempts,
        Instant createdAt,
        Instant updatedAt) {

    public JiraProjection {
        Objects.requireNonNull(projectionId, "projectionId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(milestoneId, "milestoneId");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    JiraProjection withStatus(JiraProjectionStatus next, int nextAttempts, Instant now) {
        return new JiraProjection(projectionId, ticketId, milestoneId, summary, next, nextAttempts, createdAt, now);
    }
}
