package dev.sdlc.workflow.task;

import java.util.List;

public interface AuditEventRepository {
    AuditEvent append(AuditEvent event);

    void delete(String eventId);

    List<AuditEvent> findByTaskId(String taskId);
}
