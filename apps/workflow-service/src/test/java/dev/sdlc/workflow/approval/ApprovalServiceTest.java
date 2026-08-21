package dev.sdlc.workflow.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactStore;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.artifact.FakeArtifactStore;
import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApprovalServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = { "REQUIREMENT_ANALYSIS", "DESIGN" })
    void approvalOnlyStagesCompleteWithoutCiOrManualE2e(TaskType taskType) {
        ArtifactService artifacts = new ArtifactService(new FakeArtifactStore(), new ObjectMapper(), clock);
        WorkflowTaskService tasks = new WorkflowTaskService(new InMemoryWorkflowTaskRepository(),
                new InMemoryAuditEventRepository(), new TaskTransitionPolicy(), clock);
        tasks.createTask("TASK-1", taskType,
                new WorkflowScope("DEMO-123", "REPO_A", "abc"), "key", "author", "corr");
        tasks.claimTask("TASK-1", "author", java.time.Duration.ofMinutes(15), 0, "corr");
        ArtifactType artifactType = taskType == TaskType.DESIGN
                ? ArtifactType.DESIGN_REPORT : ArtifactType.REQUIREMENT_REPORT;
        artifacts.create("ART-1", "TASK-1", artifactType,
                List.of(new ArtifactSection("summary", "Summary", "Safe content")), "author", null);
        tasks.transition("TASK-1", TaskStatus.LOCAL_COPILOT_RUNNING,
                TaskStatus.WAITING_FOR_USER_CONFIRMATION, 1, "author", "corr");
        tasks.transition("TASK-1", TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                TaskStatus.WAITING_FOR_APPROVAL, 2, "author", "corr");

        ApprovalService service = new ApprovalService(tasks, artifacts);
        ApprovalDecision decision = service.approve("TASK-1", "ART-1", 1, 3, "architect-1", "corr");

        assertThat(decision.actorId()).isEqualTo("architect-1");
        assertThat(decision.task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(decision.artifact().approvedBy()).isEqualTo("architect-1");
    }

    @Test
    void approvalIsNotVisibleUntilTaskAndAuditCommit() throws Exception {
        BlockingAuditRepository audits = new BlockingAuditRepository();
        InMemoryWorkflowTaskRepository taskRepository = new InMemoryWorkflowTaskRepository();
        ArtifactService artifacts = new ArtifactService(new FakeArtifactStore(), new ObjectMapper(), clock);
        WorkflowTaskService tasks = workflowReadyForApproval(taskRepository, audits, artifacts);
        ApprovalService service = new ApprovalService(tasks, artifacts);
        audits.blockNextAppend = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ApprovalDecision> approval = executor.submit(() ->
                    service.approve("TASK-ATOMIC", "ART-ATOMIC", 1, 3, "architect-1", "corr-approve"));
            assertThat(audits.appendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(artifacts.requireArtifact("ART-ATOMIC", 1).approved()).isFalse();
            assertThatThrownBy(() -> artifacts.requireApprovedForProjection("ART-ATOMIC", 1))
                    .isInstanceOf(IllegalArgumentException.class);

            audits.allowAppend.countDown();
            ApprovalDecision committed = approval.get(5, TimeUnit.SECONDS);
            assertThat(committed.artifact().approved()).isTrue();
            assertThat(artifacts.requireArtifact("ART-ATOMIC", 1).approved()).isTrue();
        } finally {
            audits.allowAppend.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedApprovalWithFailedArtifactRestoreIsNotPublishable() {
        AppendThenFailAuditRepository audits = new AppendThenFailAuditRepository();
        FailingSecondWriteArtifactStore store = new FailingSecondWriteArtifactStore();
        ArtifactService artifacts = new ArtifactService(store, new ObjectMapper(), clock);
        InMemoryWorkflowTaskRepository taskRepository = new InMemoryWorkflowTaskRepository();
        WorkflowTaskService tasks = workflowReadyForApproval(taskRepository, audits, artifacts);
        ApprovalService service = new ApprovalService(tasks, artifacts);
        audits.failAfterAppend = true;
        store.countWrites = true;

        assertThatThrownBy(() -> service.approve(
                "TASK-ATOMIC", "ART-ATOMIC", 1, 3, "architect-1", "corr-approve"))
                .isInstanceOf(RuntimeException.class);

        assertThat(artifacts.requireArtifact("ART-ATOMIC", 1).approved()).isFalse();
        assertThatThrownBy(() -> artifacts.requireApprovedForProjection("ART-ATOMIC", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(taskRepository.findById("TASK-ATOMIC").orElseThrow().status())
                .isEqualTo(TaskStatus.WAITING_FOR_APPROVAL);
        assertThat(audits.findByTaskId("TASK-ATOMIC")).hasSize(4);
    }

    @Test
    void orphanApprovalCannotReuseALaterArtifactsTaskVersionAndCommitEvent() {
        InMemoryAuditEventRepository audits = new InMemoryAuditEventRepository();
        PersistApprovalThenFailAndRejectRestoreStore store = new PersistApprovalThenFailAndRejectRestoreStore();
        ArtifactService artifacts = new ArtifactService(store, new ObjectMapper(), clock);
        InMemoryWorkflowTaskRepository taskRepository = new InMemoryWorkflowTaskRepository();
        WorkflowTaskService tasks = workflowReadyForApproval(taskRepository, audits, artifacts);
        ApprovalService service = new ApprovalService(tasks, artifacts);
        store.countWrites = true;

        assertThatThrownBy(() -> service.approve(
                "TASK-ATOMIC", "ART-ATOMIC", 1, 3, "architect-1", "corr-approve"))
                .isInstanceOf(RuntimeException.class);

        ArtifactMetadata orphanedApproval = artifacts.requireArtifact("ART-ATOMIC", 1);
        assertThat(orphanedApproval.approved()).isTrue();
        assertThat(taskRepository.findById("TASK-ATOMIC").orElseThrow().status())
                .isEqualTo(TaskStatus.WAITING_FOR_APPROVAL);

        artifacts.create("ART-VALID", "TASK-ATOMIC", ArtifactType.DESIGN_REPORT,
                List.of(new ArtifactSection("summary", "Summary", "Later valid content")), "author", null);
        ApprovalDecision valid = service.approve(
                "TASK-ATOMIC", "ART-VALID", 1, 3, "architect-2", "corr-valid");
        assertThat(valid.artifact().approvedTaskVersion()).isEqualTo(orphanedApproval.approvedTaskVersion());
        assertThat(valid.artifact().approvalCommitEventId())
                .isNotEqualTo(orphanedApproval.approvalCommitEventId());

        assertThatThrownBy(() -> tasks.requireCommittedApproval(
                orphanedApproval.taskId(), orphanedApproval.approvedTaskVersion(),
                orphanedApproval.artifactId(), orphanedApproval.version(),
                orphanedApproval.approvalCommitEventId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tasks.requireCommittedApproval(
                valid.artifact().taskId(), valid.artifact().approvedTaskVersion(),
                valid.artifact().artifactId(), valid.artifact().version(),
                valid.artifact().approvalCommitEventId()))
                .isEqualTo(valid.task());
    }

    @Test
    void undeletedAbortedApprovalCannotReuseALaterValidApprovalsTaskVersion() {
        DeleteFailingAuditRepository audits = new DeleteFailingAuditRepository();
        PersistApprovalThenFailAndRejectRestoreStore store = new PersistApprovalThenFailAndRejectRestoreStore();
        ArtifactService artifacts = new ArtifactService(store, new ObjectMapper(), clock);
        InMemoryWorkflowTaskRepository taskRepository = new InMemoryWorkflowTaskRepository();
        WorkflowTaskService tasks = workflowReadyForApproval(taskRepository, audits, artifacts);
        ApprovalService service = new ApprovalService(tasks, artifacts);
        audits.failDelete = true;
        store.countWrites = true;

        assertThatThrownBy(() -> service.approve(
                "TASK-ATOMIC", "ART-ATOMIC", 1, 3, "architect-1", "corr-aborted"))
                .isInstanceOf(RuntimeException.class);
        ArtifactMetadata aborted = artifacts.requireArtifact("ART-ATOMIC", 1);
        assertThat(aborted.approved()).isTrue();
        assertThat(audits.findByTaskId("TASK-ATOMIC"))
                .anyMatch(event -> event.eventId().equals(aborted.approvalCommitEventId()));
        assertThat(audits.isInvalidated(aborted.approvalCommitEventId())).isTrue();

        artifacts.create("ART-VALID-AFTER-ABORT", "TASK-ATOMIC", ArtifactType.DESIGN_REPORT,
                List.of(new ArtifactSection("summary", "Summary", "Valid replacement")), "author", null);
        ApprovalDecision valid = service.approve(
                "TASK-ATOMIC", "ART-VALID-AFTER-ABORT", 1, 3, "architect-2", "corr-valid");
        assertThat(valid.task().version()).isEqualTo(aborted.approvedTaskVersion());

        assertThatThrownBy(() -> tasks.requireCommittedApproval(
                aborted.taskId(), aborted.approvedTaskVersion(), aborted.artifactId(), aborted.version(),
                aborted.approvalCommitEventId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tasks.requireCommittedApproval(
                valid.artifact().taskId(), valid.artifact().approvedTaskVersion(),
                valid.artifact().artifactId(), valid.artifact().version(),
                valid.artifact().approvalCommitEventId()))
                .isEqualTo(valid.task());
    }

    @Test
    void committedApprovalRemainsValidThroughAContinuousNonInvalidatedTaskAuditChain() {
        InMemoryAuditEventRepository audits = new InMemoryAuditEventRepository();
        InMemoryWorkflowTaskRepository taskRepository = new InMemoryWorkflowTaskRepository();
        ArtifactService artifacts = new ArtifactService(new FakeArtifactStore(), new ObjectMapper(), clock);
        WorkflowTaskService tasks = new WorkflowTaskService(
                taskRepository, audits, new TaskTransitionPolicy(), clock);
        tasks.createTask("TASK-CHAIN", TaskType.IMPLEMENTATION,
                new WorkflowScope("DEMO-CHAIN", "REPO_A", "chain-ref"),
                "chain", "author", "corr");
        tasks.claimTask("TASK-CHAIN", "author", java.time.Duration.ofMinutes(15), 0, "corr");
        tasks.transition("TASK-CHAIN", TaskStatus.LOCAL_COPILOT_RUNNING,
                TaskStatus.WAITING_FOR_USER_CONFIRMATION, 1, "author", "corr");
        tasks.transition("TASK-CHAIN", TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                TaskStatus.WAITING_FOR_APPROVAL, 2, "author", "corr");
        artifacts.create("ART-CHAIN", "TASK-CHAIN", ArtifactType.DELIVERY_REPORT,
                List.of(new ArtifactSection("summary", "Summary", "Implementation evidence")), "author", null);
        ApprovalDecision approval = new ApprovalService(tasks, artifacts).approve(
                "TASK-CHAIN", "ART-CHAIN", 1, 3, "architect", "corr-approve");

        WorkflowTask completed = tasks.transitionAfterPassedCi(
                "TASK-CHAIN", approval.task().version(), "ci", "corr-ci");

        assertThat(tasks.requireCommittedApproval(
                approval.artifact().taskId(), approval.artifact().approvedTaskVersion(),
                approval.artifact().artifactId(), approval.artifact().version(),
                approval.artifact().approvalCommitEventId()))
                .isEqualTo(completed);
    }

    private WorkflowTaskService workflowReadyForApproval(
            InMemoryWorkflowTaskRepository taskRepository,
            AuditEventRepository audits,
            ArtifactService artifacts) {
        WorkflowTaskService tasks = new WorkflowTaskService(taskRepository, audits, new TaskTransitionPolicy(), clock);
        tasks.createTask("TASK-ATOMIC", TaskType.DESIGN,
                new WorkflowScope("DEMO-ATOMIC", "REPO_A", "atomic-ref"), "atomic", "author", "corr");
        tasks.claimTask("TASK-ATOMIC", "author", java.time.Duration.ofMinutes(15), 0, "corr");
        tasks.transition("TASK-ATOMIC", TaskStatus.LOCAL_COPILOT_RUNNING,
                TaskStatus.WAITING_FOR_USER_CONFIRMATION, 1, "author", "corr");
        tasks.transition("TASK-ATOMIC", TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                TaskStatus.WAITING_FOR_APPROVAL, 2, "author", "corr");
        artifacts.create("ART-ATOMIC", "TASK-ATOMIC", ArtifactType.DESIGN_REPORT,
                List.of(new ArtifactSection("summary", "Summary", "Safe content")), "author", null);
        return tasks;
    }

    private static final class BlockingAuditRepository implements AuditEventRepository {
        private final InMemoryAuditEventRepository delegate = new InMemoryAuditEventRepository();
        private final CountDownLatch appendStarted = new CountDownLatch(1);
        private final CountDownLatch allowAppend = new CountDownLatch(1);
        private boolean blockNextAppend;

        @Override
        public AuditEvent append(AuditEvent event) {
            if (blockNextAppend) {
                appendStarted.countDown();
                try {
                    if (!allowAppend.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("append timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("append interrupted", exception);
                }
            }
            return delegate.append(event);
        }

        @Override
        public void delete(String eventId) { delegate.delete(eventId); }

        @Override
        public void invalidate(String eventId) { delegate.invalidate(eventId); }

        @Override
        public boolean isInvalidated(String eventId) { return delegate.isInvalidated(eventId); }

        @Override
        public List<AuditEvent> findByTaskId(String taskId) { return delegate.findByTaskId(taskId); }
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
        public void invalidate(String eventId) { delegate.invalidate(eventId); }

        @Override
        public boolean isInvalidated(String eventId) { return delegate.isInvalidated(eventId); }

        @Override
        public List<AuditEvent> findByTaskId(String taskId) { return delegate.findByTaskId(taskId); }
    }

    private static final class DeleteFailingAuditRepository implements AuditEventRepository {
        private final InMemoryAuditEventRepository delegate = new InMemoryAuditEventRepository();
        private boolean failDelete;

        @Override
        public AuditEvent append(AuditEvent event) { return delegate.append(event); }

        @Override
        public void delete(String eventId) {
            if (failDelete) throw new IllegalStateException("audit delete failed");
            delegate.delete(eventId);
        }

        @Override
        public void invalidate(String eventId) { delegate.invalidate(eventId); }

        @Override
        public boolean isInvalidated(String eventId) { return delegate.isInvalidated(eventId); }

        @Override
        public List<AuditEvent> findByTaskId(String taskId) { return delegate.findByTaskId(taskId); }
    }

    private static final class FailingSecondWriteArtifactStore implements ArtifactStore {
        private final FakeArtifactStore delegate = new FakeArtifactStore();
        private boolean countWrites;
        private int writes;

        @Override
        public ArtifactMetadata save(ArtifactMetadata artifact) {
            if (countWrites && ++writes == 2) throw new IllegalStateException("artifact restore failed");
            return delegate.save(artifact);
        }

        @Override
        public void delete(String artifactId, int version) { delegate.delete(artifactId, version); }

        @Override
        public Optional<ArtifactMetadata> find(String artifactId, int version) {
            return delegate.find(artifactId, version);
        }

        @Override
        public Optional<ArtifactMetadata> findLatest(String artifactId) { return delegate.findLatest(artifactId); }
    }

    private static final class PersistApprovalThenFailAndRejectRestoreStore implements ArtifactStore {
        private final FakeArtifactStore delegate = new FakeArtifactStore();
        private boolean countWrites;
        private int writes;

        @Override
        public ArtifactMetadata save(ArtifactMetadata artifact) {
            if (!countWrites) return delegate.save(artifact);
            writes++;
            if (writes == 2) {
                delegate.save(artifact);
                throw new IllegalStateException("approval response was lost after persistence");
            }
            if (writes == 3) throw new IllegalStateException("artifact restore failed");
            return delegate.save(artifact);
        }

        @Override
        public void delete(String artifactId, int version) { delegate.delete(artifactId, version); }

        @Override
        public Optional<ArtifactMetadata> find(String artifactId, int version) {
            return delegate.find(artifactId, version);
        }

        @Override
        public Optional<ArtifactMetadata> findLatest(String artifactId) { return delegate.findLatest(artifactId); }
    }
}
