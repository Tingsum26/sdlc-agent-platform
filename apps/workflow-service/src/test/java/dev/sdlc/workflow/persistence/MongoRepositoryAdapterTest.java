package dev.sdlc.workflow.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

class MongoRepositoryAdapterTest {

    @Test
    void taskUpdateUsesPreviousVersionInOptimisticQuery() {
        MongoOperations mongo = mock(MongoOperations.class);
        MongoWorkflowTaskRepository repository = new MongoWorkflowTaskRepository(mongo);
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        WorkflowTask task = new WorkflowTask("TASK-1", TaskType.DESIGN, TaskStatus.WAITING_FOR_APPROVAL,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "idem-1", null, null, 7, now, now);
        WorkflowTaskDocument document = WorkflowTaskDocument.fromDomain(task);
        when(mongo.findAndReplace(any(Query.class), any(WorkflowTaskDocument.class),
                any(FindAndReplaceOptions.class), eq("workflowTasks"))).thenReturn(document);

        assertEquals(task, repository.save(task));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndReplace(query.capture(), eq(document), any(FindAndReplaceOptions.class), eq("workflowTasks"));
        Document filter = query.getValue().getQueryObject();
        assertEquals("TASK-1", filter.getString("_id"));
        assertEquals(6L, ((Number) filter.get("version")).longValue());
    }
}
