package dev.sdlc.workflow.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InMemoryAuditEventRepository implements AuditEventRepository {

    private final List<AuditEvent> events = new ArrayList<>();
    private final Set<String> invalidatedEventIds = new HashSet<>();

    @Override
    public synchronized AuditEvent append(AuditEvent event) {
        if (invalidatedEventIds.contains(event.eventId())
                || events.stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) {
            throw new IllegalStateException("Audit event ID cannot be reused");
        }
        events.add(event);
        return event;
    }

    @Override
    public synchronized void delete(String eventId) {
        events.removeIf(event -> event.eventId().equals(eventId));
    }

    @Override
    public synchronized void invalidate(String eventId) {
        invalidatedEventIds.add(eventId);
    }

    @Override
    public synchronized boolean isInvalidated(String eventId) {
        return invalidatedEventIds.contains(eventId);
    }

    @Override
    public synchronized List<AuditEvent> findByTaskId(String taskId) {
        return events.stream().filter(event -> event.taskId().equals(taskId)).toList();
    }
}
