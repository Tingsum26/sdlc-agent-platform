package dev.sdlc.workflow.task;

public record WorkflowTaskCommit(WorkflowTask task, AuditEvent auditEvent) {
}
