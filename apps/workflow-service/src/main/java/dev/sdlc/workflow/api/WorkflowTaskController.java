package dev.sdlc.workflow.api;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.security.CurrentUser;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkflowTaskController {

    private final WorkflowTaskService tasks;
    private final ArtifactService artifacts;
    private final ApprovalService approvals;

    public WorkflowTaskController(WorkflowTaskService tasks, ArtifactService artifacts, ApprovalService approvals) {
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.approvals = approvals;
    }

    @PostMapping("/workflows/from-ticket")
    ResponseEntity<WorkflowTaskResponse> createFromTicket(
            @Valid @RequestBody CreateWorkflowRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        String taskId = "TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        WorkflowScope scope = new WorkflowScope(body.ticketId(), body.repositoryAlias(), body.targetCommit());
        WorkflowTask task = tasks.createTask(taskId, TaskType.REQUIREMENT_ANALYSIS, scope,
                "ticket:" + body.ticketId() + ":" + body.targetCommit(), user.actorId(), CorrelationIdFilter.from(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowTaskResponse.from(task));
    }

    @GetMapping("/tasks")
    List<WorkflowTaskResponse> list(HttpServletRequest request) {
        CurrentUser.require(request);
        return tasks.listTasks().stream().map(WorkflowTaskResponse::from).toList();
    }

    @PostMapping("/tasks/{taskId}/claim")
    WorkflowTaskResponse claim(
            @PathVariable String taskId,
            @Valid @RequestBody ClaimTaskRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return WorkflowTaskResponse.from(tasks.claimTask(taskId, user.actorId(), Duration.ofMinutes(body.leaseMinutes()),
                body.expectedVersion(), CorrelationIdFilter.from(request)));
    }

    @PostMapping("/tasks/{taskId}/results")
    ArtifactMetadata submitResult(
            @PathVariable String taskId,
            @Valid @RequestBody SubmitArtifactRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        WorkflowTask task = tasks.getTask(taskId);
        ArtifactMetadata artifact = artifacts.create(body.artifactId(), taskId, body.type(), body.sections(),
                user.actorId(), body.contentHash());
        tasks.transition(taskId, TaskStatus.LOCAL_COPILOT_RUNNING, TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                task.version(), user.actorId(), CorrelationIdFilter.from(request));
        return artifact;
    }

    @PostMapping("/tasks/{taskId}/confirm")
    WorkflowTaskResponse confirm(
            @PathVariable String taskId,
            @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return WorkflowTaskResponse.from(tasks.transition(taskId, TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                TaskStatus.WAITING_FOR_APPROVAL, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request)));
    }

    @PostMapping("/approvals")
    WorkflowTaskResponse approve(
            @Valid @RequestBody ApprovalRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return WorkflowTaskResponse.from(approvals.approve(body.taskId(), body.artifactId(), body.artifactVersion(),
                body.expectedTaskVersion(), user.actorId(), CorrelationIdFilter.from(request)).task());
    }

    @GetMapping(value = "/reports/{artifactId}/versions/{version}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> report(
            @PathVariable String artifactId,
            @PathVariable int version,
            HttpServletRequest request) {
        CurrentUser.require(request);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(artifacts.renderHtml(artifactId, version));
    }

    public record CreateWorkflowRequest(
            @NotBlank String ticketId,
            @NotBlank String repositoryAlias,
            @NotBlank String targetCommit) {
    }

    public record ClaimTaskRequest(long expectedVersion, @Min(1) @Max(120) int leaseMinutes) {
    }

    public record VersionRequest(long expectedVersion) {
    }

    public record SubmitArtifactRequest(
            @NotBlank String artifactId,
            @NotNull ArtifactType type,
            @NotEmpty List<@Valid ArtifactSection> sections,
            String contentHash) {
    }

    public record ApprovalRequest(
            @NotBlank String taskId,
            @NotBlank String artifactId,
            @Min(1) int artifactVersion,
            @Min(0) long expectedTaskVersion) {
    }
}
