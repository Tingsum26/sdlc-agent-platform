package dev.sdlc.workflow.task;

import java.util.List;

public interface AuditEventRepository {
    AuditEvent append(AuditEvent event);

    List<AuditEvent> findByTaskId(String taskId);
}
