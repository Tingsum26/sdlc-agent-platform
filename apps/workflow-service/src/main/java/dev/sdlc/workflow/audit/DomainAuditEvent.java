package dev.sdlc.workflow.audit;

import java.time.Instant;
import java.util.Objects;

public record DomainAuditEvent(
        String eventId,
        String aggregateId,
        String aggregateType,
        String action,
        String detail,
        String actorId,
        Instant occurredAt,
        String correlationId) {

    public DomainAuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
