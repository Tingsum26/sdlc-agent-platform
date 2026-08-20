package dev.sdlc.workflow.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.artifact.FakeArtifactStore;
import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
}
