package dev.sdlc.workflow.task;

import java.util.List;

public interface AuditEventRepository {
    AuditEvent append(AuditEvent event);

    void delete(String eventId);

    void invalidate(String eventId);

    boolean isInvalidated(String eventId);

    List<AuditEvent> findByTaskId(String taskId);
}
