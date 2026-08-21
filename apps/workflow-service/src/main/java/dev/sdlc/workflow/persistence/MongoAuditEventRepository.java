package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.AuditEventRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoAuditEventRepository implements AuditEventRepository {
    static final String COLLECTION = "auditEvents";
    static final String INVALIDATIONS_COLLECTION = "auditEventInvalidations";
    private final MongoOperations mongo;

    public MongoAuditEventRepository(MongoOperations mongo) { this.mongo = mongo; }

    @Override
    public AuditEvent append(AuditEvent event) {
        return mongo.insert(AuditEventDocument.fromDomain(event), COLLECTION).toDomain();
    }

    @Override
    public void delete(String eventId) {
        mongo.remove(Query.query(Criteria.where("_id").is(eventId)), AuditEventDocument.class, COLLECTION);
    }

    @Override
    public void invalidate(String eventId) {
        mongo.save(new AuditEventInvalidationDocument(eventId), INVALIDATIONS_COLLECTION);
    }

    @Override
    public boolean isInvalidated(String eventId) {
        return mongo.exists(Query.query(Criteria.where("_id").is(eventId)), INVALIDATIONS_COLLECTION);
    }

    @Override
    public List<AuditEvent> findByTaskId(String taskId) {
        Query query = Query.query(Criteria.where("taskId").is(taskId)).with(Sort.by("sequence"));
        return mongo.find(query, AuditEventDocument.class, COLLECTION).stream().map(AuditEventDocument::toDomain).toList();
    }
}
