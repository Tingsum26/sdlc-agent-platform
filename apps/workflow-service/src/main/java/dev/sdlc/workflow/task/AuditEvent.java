package dev.sdlc.workflow.task;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Instant;

public record AuditEvent(
        String eventId,
        String taskId,
        long sequence,
        String actorId,
        String action,
        EvidenceClassification evidenceClassification,
        TaskStatus previousStatus,
        TaskStatus newStatus,
        long taskVersion,
        Instant occurredAt,
        String correlationId) {

    public AuditEvent(String eventId, String taskId, long sequence, String actorId, String action,
            TaskStatus previousStatus, TaskStatus newStatus, long taskVersion, Instant occurredAt,
            String correlationId) {
        this(eventId, taskId, sequence, actorId, action, EvidenceClassification.REAL,
                previousStatus, newStatus, taskVersion, occurredAt, correlationId);
    }
}
