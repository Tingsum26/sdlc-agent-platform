package dev.sdlc.workflow.audit;

import java.util.List;

public interface DomainAuditEventRepository {
    DomainAuditEvent append(DomainAuditEvent event);
    List<DomainAuditEvent> findByAggregateId(String aggregateId);
}
