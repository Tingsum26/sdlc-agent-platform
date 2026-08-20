package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.evidence.EvidenceClassification;
import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.TaskStatus;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("auditEvents")
public record AuditEventDocument(
        @Id String eventId, String taskId, long sequence, String actorId, String action,
        EvidenceClassification evidenceClassification,
        TaskStatus previousStatus, TaskStatus newStatus, long taskVersion, Instant occurredAt, String correlationId) {
    public static AuditEventDocument fromDomain(AuditEvent event) {
        return new AuditEventDocument(event.eventId(), event.taskId(), event.sequence(), event.actorId(), event.action(),
                event.evidenceClassification(),
                event.previousStatus(), event.newStatus(), event.taskVersion(), event.occurredAt(), event.correlationId());
    }

    public AuditEvent toDomain() {
        return new AuditEvent(eventId, taskId, sequence, actorId, action,
                evidenceClassification == null ? EvidenceClassification.REAL : evidenceClassification,
                previousStatus, newStatus,
                taskVersion, occurredAt, correlationId);
    }
}
