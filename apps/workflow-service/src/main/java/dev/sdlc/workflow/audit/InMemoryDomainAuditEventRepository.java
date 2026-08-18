package dev.sdlc.workflow.audit;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryDomainAuditEventRepository implements DomainAuditEventRepository {
    private final List<DomainAuditEvent> events = new ArrayList<>();

    @Override
    public synchronized DomainAuditEvent append(DomainAuditEvent event) {
        events.add(event);
        return event;
    }

    @Override
    public synchronized List<DomainAuditEvent> findByAggregateId(String aggregateId) {
        return events.stream().filter(event -> event.aggregateId().equals(aggregateId)).toList();
    }
}
