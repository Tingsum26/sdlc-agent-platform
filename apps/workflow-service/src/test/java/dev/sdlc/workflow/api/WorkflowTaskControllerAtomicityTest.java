package dev.sdlc.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.artifact.ArtifactNotFoundException;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.artifact.FakeArtifactStore;
import dev.sdlc.workflow.security.CurrentUser;
import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WorkflowTaskControllerAtomicityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void resultSubmissionCompensatesArtifactAndTaskWhenAuditCommitFails() {
        InMemoryWorkflowTaskRepository repository = new InMemoryWorkflowTaskRepository();
        AppendThenFailAuditRepository audits = new AppendThenFailAuditRepository();
        WorkflowTaskService tasks = new WorkflowTaskService(repository, audits, new TaskTransitionPolicy(), CLOCK);
        ArtifactService artifacts = new ArtifactService(new FakeArtifactStore(), new ObjectMapper(), CLOCK);
        ApprovalService approvals = new ApprovalService(tasks, artifacts);
        WorkflowTaskController controller = new WorkflowTaskController(tasks, artifacts, approvals, null);
        WorkflowTask created = tasks.createTask("TASK-RESULT-AUDIT", TaskType.DESIGN,
                new WorkflowScope("DEMO-RESULT-AUDIT", "REPO_A", "result-ref"),
                "result-audit", "developer-1", "corr-create");
        WorkflowTask claimed = tasks.claimTask(created.taskId(), "developer-1", Duration.ofMinutes(15),
                created.version(), "corr-claim");
        audits.failAfterAppend = true;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, new CurrentUser("developer-1"));

        assertThatThrownBy(() -> controller.submitResult(claimed.taskId(),
                new WorkflowTaskController.SubmitArtifactRequest("ART-RESULT-AUDIT", ArtifactType.DESIGN_REPORT,
                        List.of(new ArtifactSection("summary", "Summary", "Safe fictional result")), null),
                request))
                .isInstanceOf(IllegalStateException.class);

        WorkflowTask restored = repository.findById(claimed.taskId()).orElseThrow();
        assertThat(restored.status()).isEqualTo(TaskStatus.LOCAL_COPILOT_RUNNING);
        assertThat(restored.version()).isEqualTo(claimed.version());
        assertThatThrownBy(() -> artifacts.requireArtifact("ART-RESULT-AUDIT", 1))
                .isInstanceOf(ArtifactNotFoundException.class);
        assertThat(audits.findByTaskId(claimed.taskId())).hasSize(2);
    }

    @Test
    void claimApiRestoresOriginalTaskWhenRepositoryThrowsAfterPersistingSave() {
        PersistThenThrowWorkflowTaskRepository repository = new PersistThenThrowWorkflowTaskRepository();
        InMemoryAuditEventRepository audits = new InMemoryAuditEventRepository();
        WorkflowTaskService tasks = new WorkflowTaskService(repository, audits, new TaskTransitionPolicy(), CLOCK);
        ArtifactService artifacts = new ArtifactService(new FakeArtifactStore(), new ObjectMapper(), CLOCK);
        WorkflowTaskController controller = new WorkflowTaskController(
                tasks, artifacts, new ApprovalService(tasks, artifacts), null);
        WorkflowTask original = tasks.createTask("TASK-SAVE-UNCERTAIN", TaskType.DESIGN,
                new WorkflowScope("DEMO-SAVE-UNCERTAIN", "REPO_A", "save-ref"),
                "save-uncertain", "developer-1", "corr-create");
        repository.failAfterSave = true;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, new CurrentUser("developer-1"));

        assertThatThrownBy(() -> controller.claim(original.taskId(),
                new WorkflowTaskController.ClaimTaskRequest(original.version(), 15), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task save failed after persistence");

        assertThat(repository.findById(original.taskId())).contains(original);
        assertThat(audits.findByTaskId(original.taskId()))
                .extracting(AuditEvent::action)
                .containsExactly("TASK_CREATED");
    }

    private static final class AppendThenFailAuditRepository implements AuditEventRepository {
        private final InMemoryAuditEventRepository delegate = new InMemoryAuditEventRepository();
        private boolean failAfterAppend;

        @Override
        public AuditEvent append(AuditEvent event) {
            AuditEvent appended = delegate.append(event);
            if (failAfterAppend) throw new IllegalStateException("audit persistence failed after append");
            return appended;
        }

        @Override
        public void delete(String eventId) { delegate.delete(eventId); }

        @Override
        public List<AuditEvent> findByTaskId(String taskId) {
            return delegate.findByTaskId(taskId);
        }
    }

    private static final class PersistThenThrowWorkflowTaskRepository implements WorkflowTaskRepository {
        private final InMemoryWorkflowTaskRepository delegate = new InMemoryWorkflowTaskRepository();
        private boolean failAfterSave;

        @Override
        public Optional<WorkflowTask> findById(String taskId) { return delegate.findById(taskId); }

        @Override
        public Optional<WorkflowTask> findByIdempotencyKey(String idempotencyKey) {
            return delegate.findByIdempotencyKey(idempotencyKey);
        }

        @Override
        public List<WorkflowTask> findAll() { return delegate.findAll(); }

        @Override
        public WorkflowTask save(WorkflowTask task) {
            WorkflowTask saved = delegate.save(task);
            if (failAfterSave) throw new IllegalStateException("task save failed after persistence");
            return saved;
        }

        @Override
        public void restore(WorkflowTask task, long expectedCurrentVersion) {
            delegate.restore(task, expectedCurrentVersion);
        }

        @Override
        public void delete(String taskId, long expectedVersion) { delegate.delete(taskId, expectedVersion); }
    }
}
