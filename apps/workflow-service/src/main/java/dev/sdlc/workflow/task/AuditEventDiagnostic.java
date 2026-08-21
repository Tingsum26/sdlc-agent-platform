package dev.sdlc.workflow.task;

/**
 * Diagnostic-only view of an audit event, including durable compensation state.
 * Normal workflow consumers must use the valid-only audit stream instead.
 */
public record AuditEventDiagnostic(AuditEvent event, boolean invalidated) {
}
