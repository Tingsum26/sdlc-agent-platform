package dev.sdlc.workflow.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("auditEventInvalidations")
public record AuditEventInvalidationDocument(@Id String eventId) {
}
