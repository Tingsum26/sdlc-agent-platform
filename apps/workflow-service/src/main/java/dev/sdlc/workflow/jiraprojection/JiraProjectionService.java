package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.JiraProjectionClient;
import dev.sdlc.workflow.artifact.JiraProjectionStatus;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JiraProjectionService {

    public static final int MAX_ATTEMPTS = 3;

    private final JiraProjectionRepository projections;
    private final JiraProjectionClient client;
    private final Clock clock;

    public JiraProjectionService(JiraProjectionRepository projections, JiraProjectionClient client, Clock clock) {
        this.projections = projections;
        this.client = client;
        this.clock = clock;
    }

    public synchronized JiraProjection enqueue(String ticketId, String milestoneId, String serverGeneratedSummary,
            String actorId, String correlationId) {
        if (ticketId == null || ticketId.isBlank()) throw new IllegalArgumentException("ticketId is required");
        if (milestoneId == null || milestoneId.isBlank()) throw new IllegalArgumentException("milestoneId is required");
        if (serverGeneratedSummary == null || serverGeneratedSummary.isBlank()) {
            throw new IllegalArgumentException("server-generated summary is required");
        }
        if (serverGeneratedSummary.length() > JiraSummaryFactory.MAX_LENGTH) {
            throw new IllegalArgumentException("server-generated summary exceeds 500 characters");
        }
        JiraProjection existing = projections.findAll().stream()
                .filter(item -> item.ticketId().equals(ticketId) && item.milestoneId().equals(milestoneId))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        Instant now = clock.instant();
        String projectionId = "JIRA-PROJ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        JiraProjection draft = new JiraProjection(projectionId, ticketId, milestoneId, serverGeneratedSummary,
                JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, 0, now, now);
        projections.save(draft);
        return draft;
    }

    public synchronized List<JiraProjection> flushPending(String actorId, String correlationId) {
        List<JiraProjection> changed = new ArrayList<>();
        for (JiraProjection projection : projections.findAll()) {
            if (projection.status() != JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING) continue;
            JiraProjection updated = attempt(projection);
            projections.save(updated);
            changed.add(updated);
        }
        return changed;
    }

    public synchronized JiraProjection flush(String projectionId, String actorId, String correlationId) {
        JiraProjection projection = get(projectionId);
        if (projection.status() != JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING) {
            throw new WorkflowConflictException("Projection is not pending");
        }
        JiraProjection updated = attempt(projection);
        projections.save(updated);
        return updated;
    }

    public JiraProjection get(String projectionId) {
        return projections.findById(projectionId)
                .orElseThrow(() -> new IllegalArgumentException("Projection not found: " + projectionId));
    }

    public List<JiraProjection> listAll() {
        return projections.findAll();
    }

    private JiraProjection attempt(JiraProjection projection) {
        try {
            client.publish(projection.ticketId(), projection.summary(), "");
            return projection.withStatus(JiraProjectionStatus.PUBLISHED,
                    projection.attempts() + 1, clock.instant());
        } catch (RuntimeException exception) {
            int attempts = projection.attempts() + 1;
            JiraProjectionStatus status = attempts >= MAX_ATTEMPTS
                    ? JiraProjectionStatus.JIRA_ARTIFACT_SYNC_FAILED
                    : JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING;
            return projection.withStatus(status, attempts, clock.instant());
        }
    }
}
