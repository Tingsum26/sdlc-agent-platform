package dev.sdlc.workflow.task;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryAuditEventRepository implements AuditEventRepository {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public synchronized AuditEvent append(AuditEvent event) {
        events.add(event);
        return event;
    }

    @Override
    public synchronized void delete(String eventId) {
        events.removeIf(event -> event.eventId().equals(eventId));
    }

    @Override
    public synchronized List<AuditEvent> findByTaskId(String taskId) {
        return events.stream().filter(event -> event.taskId().equals(taskId)).toList();
    }
}
