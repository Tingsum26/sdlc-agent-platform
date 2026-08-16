package dev.sdlc.workflow.task;

import java.time.Instant;

public record AuditEvent(
        String eventId,
        String taskId,
        long sequence,
        String actorId,
        String action,
        TaskStatus previousStatus,
        TaskStatus newStatus,
        long taskVersion,
        Instant occurredAt,
        String correlationId) {
}
