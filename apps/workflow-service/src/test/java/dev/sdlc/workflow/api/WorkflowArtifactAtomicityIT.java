package dev.sdlc.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sdlc.workflow.artifact.ArtifactNotFoundException;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.jiraprojection.JiraProjectionRepository;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fake")
class WorkflowArtifactAtomicityIT {
    @Autowired MockMvc mvc;
    @Autowired WorkflowTaskRepository tasks;
    @Autowired ArtifactService artifacts;
    @Autowired JiraProjectionRepository jiraProjections;

    @Test
    void rejectedResultLeavesNoArtifactOrTaskMutation() throws Exception {
        seed("TASK-RESULT-STATE", TaskType.REQUIREMENT_ANALYSIS,
                TaskStatus.WAITING_FOR_LOCAL_COPILOT, 0);

        mvc.perform(post("/api/v1/tasks/TASK-RESULT-STATE/results")
                        .header("X-Demo-User", "developer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(result("ART-RESULT-STATE", "REQUIREMENT_REPORT")))
                .andExpect(status().isConflict());

        assertThatThrownBy(() -> artifacts.requireArtifact("ART-RESULT-STATE", 1))
                .isInstanceOf(ArtifactNotFoundException.class);
        assertThat(tasks.findById("TASK-RESULT-STATE").orElseThrow().status())
                .isEqualTo(TaskStatus.WAITING_FOR_LOCAL_COPILOT);
    }

    @Test
    void resultTypeMustMatchTaskTypeBeforeAnythingIsPersisted() throws Exception {
        seed("TASK-RESULT-TYPE", TaskType.DESIGN, TaskStatus.LOCAL_COPILOT_RUNNING, 1);

        mvc.perform(post("/api/v1/tasks/TASK-RESULT-TYPE/results")
                        .header("X-Demo-User", "developer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(result("ART-RESULT-TYPE", "REQUIREMENT_REPORT")))
                .andExpect(status().isBadRequest());

        assertThatThrownBy(() -> artifacts.requireArtifact("ART-RESULT-TYPE", 1))
                .isInstanceOf(ArtifactNotFoundException.class);
        assertThat(tasks.findById("TASK-RESULT-TYPE").orElseThrow().status())
                .isEqualTo(TaskStatus.LOCAL_COPILOT_RUNNING);
    }

    @Test
    void staleApprovalLeavesArtifactUnapprovedAndCannotCreateAJiraProjection() throws Exception {
        seed("TASK-APPROVAL-STALE", TaskType.DESIGN, TaskStatus.WAITING_FOR_APPROVAL, 3);
        artifacts.create("ART-APPROVAL-STALE", "TASK-APPROVAL-STALE", ArtifactType.DESIGN_REPORT,
                sections(), "developer-1", null);

        mvc.perform(post("/api/v1/approvals")
                        .header("X-Demo-User", "architect-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approval("TASK-APPROVAL-STALE", "ART-APPROVAL-STALE", 2)))
                .andExpect(status().isConflict());

        assertThat(artifacts.requireArtifact("ART-APPROVAL-STALE", 1).approved()).isFalse();
        assertThat(jiraProjections.findAll()).noneMatch(item -> item.ticketId().equals("TICKET-APPROVAL-STALE"));
    }

    @Test
    void approvalRevalidatesArtifactTypeBeforeApprovalOrJiraProjection() throws Exception {
        seed("TASK-APPROVAL-TYPE", TaskType.DESIGN, TaskStatus.WAITING_FOR_APPROVAL, 3);
        artifacts.create("ART-APPROVAL-TYPE", "TASK-APPROVAL-TYPE", ArtifactType.REQUIREMENT_REPORT,
                sections(), "developer-1", null);

        mvc.perform(post("/api/v1/approvals")
                        .header("X-Demo-User", "architect-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approval("TASK-APPROVAL-TYPE", "ART-APPROVAL-TYPE", 3)))
                .andExpect(status().isBadRequest());

        assertThat(artifacts.requireArtifact("ART-APPROVAL-TYPE", 1).approved()).isFalse();
        assertThat(jiraProjections.findAll()).noneMatch(item -> item.ticketId().equals("TICKET-APPROVAL-TYPE"));
    }

    private void seed(String taskId, TaskType type, TaskStatus status, long version) {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        String ticketId = taskId.replace("TASK-", "TICKET-");
        tasks.save(new WorkflowTask(taskId, type, status,
                new WorkflowScope(ticketId, "REPO_A", "0123456789abcdef"),
                "atomic-" + taskId, "developer-1", null, version, now, now));
    }

    private static String result(String artifactId, String type) {
        return "{\"artifactId\":\"%s\",\"type\":\"%s\",\"sections\":[{\"key\":\"summary\",\"title\":\"Summary\",\"body\":\"Safe fictional result\"}]}"
                .formatted(artifactId, type);
    }

    private static String approval(String taskId, String artifactId, long expectedVersion) {
        return "{\"taskId\":\"%s\",\"artifactId\":\"%s\",\"artifactVersion\":1,\"expectedTaskVersion\":%d}"
                .formatted(taskId, artifactId, expectedVersion);
    }

    private static List<ArtifactSection> sections() {
        return List.of(new ArtifactSection("summary", "Summary", "Safe fictional artifact"));
    }
}
