package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.assignment.TaskAssignment;
import dev.sdlc.workflow.assignment.TaskAssignmentRepository;
import dev.sdlc.workflow.task.StaleTaskVersionException;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoTaskAssignmentRepository implements TaskAssignmentRepository {
    static final String COLLECTION = "taskAssignments";
    private final MongoOperations mongo;

    public MongoTaskAssignmentRepository(MongoOperations mongo) { this.mongo = mongo; }

    @Override
    public Optional<TaskAssignment> find(String ticketId) {
        return Optional.ofNullable(mongo.findById(ticketId, TaskAssignmentDocument.class, COLLECTION))
                .map(TaskAssignmentDocument::toDomain);
    }

    @Override
    public TaskAssignment save(TaskAssignment assignment) {
        TaskAssignmentDocument document = TaskAssignmentDocument.fromDomain(assignment);
        if (assignment.version() == 1) {
            try {
                return mongo.insert(document, COLLECTION).toDomain();
            } catch (DuplicateKeyException exception) {
                throw new StaleTaskVersionException("Assignment version changed");
            }
        }
        Query query = Query.query(Criteria.where("_id").is(assignment.ticketId())
                .and("version").is(assignment.version() - 1));
        TaskAssignmentDocument saved = mongo.findAndReplace(query, document,
                FindAndReplaceOptions.options().returnNew(), COLLECTION);
        if (saved == null) throw new StaleTaskVersionException("Assignment version changed");
        return saved.toDomain();
    }
}
