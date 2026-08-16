package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.task.StaleTaskVersionException;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoWorkflowTaskRepository implements WorkflowTaskRepository {
    static final String COLLECTION = "workflowTasks";
    private final MongoOperations mongo;

    public MongoWorkflowTaskRepository(MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<WorkflowTask> findById(String taskId) {
        return Optional.ofNullable(mongo.findById(taskId, WorkflowTaskDocument.class, COLLECTION))
                .map(WorkflowTaskDocument::toDomain);
    }

    @Override
    public Optional<WorkflowTask> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("idempotencyKey").is(idempotencyKey)),
                WorkflowTaskDocument.class, COLLECTION)).map(WorkflowTaskDocument::toDomain);
    }

    @Override
    public List<WorkflowTask> findAll() {
        return mongo.findAll(WorkflowTaskDocument.class, COLLECTION).stream().map(WorkflowTaskDocument::toDomain).toList();
    }

    @Override
    public WorkflowTask save(WorkflowTask task) {
        WorkflowTaskDocument document = WorkflowTaskDocument.fromDomain(task);
        if (task.version() == 0) {
            try {
                return mongo.insert(document, COLLECTION).toDomain();
            } catch (DuplicateKeyException exception) {
                throw new StaleTaskVersionException("Workflow task already exists");
            }
        }
        Query query = Query.query(Criteria.where("_id").is(task.taskId()).and("version").is(task.version() - 1));
        WorkflowTaskDocument saved = mongo.findAndReplace(query, document,
                FindAndReplaceOptions.options().returnNew(), COLLECTION);
        if (saved == null) throw new StaleTaskVersionException("Workflow task version changed");
        return saved.toDomain();
    }
}
