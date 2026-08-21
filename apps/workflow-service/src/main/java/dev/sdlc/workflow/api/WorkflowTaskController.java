package dev.sdlc.workflow.api;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.artifact.TaskArtifactPolicy;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.security.CurrentUser;
import dev.sdlc.workflow.evidence.EvidenceClassification;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.AuditEventDiagnostic;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
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
    private final TicketWorkflowService tickets;

    public WorkflowTaskController(WorkflowTaskService tasks, ArtifactService artifacts, ApprovalService approvals,
            TicketWorkflowService tickets) {
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.approvals = approvals;
        this.tickets = tickets;
    }

    @PostMapping("/workflows/from-ticket")
    ResponseEntity<WorkflowTaskResponse> createFromTicket(
            @Valid @RequestBody CreateWorkflowRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        String taskId = "TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        WorkflowScope scope = new WorkflowScope(body.ticketId(), body.repositoryAlias(), body.targetCommit());
        TaskType type = body.type() == null ? TaskType.REQUIREMENT_ANALYSIS : body.type();
        EvidenceClassification evidenceClassification = classificationFor(body.ticketId(), body.evidenceClassification());
        requireClassificationActor(evidenceClassification, user);
        String previousIdempotencyKey = "ticket:" + body.ticketId() + ":" + body.repositoryAlias()
                + ":" + body.targetCommit() + ":" + type;
        String idempotencyKey = previousIdempotencyKey + ":" + evidenceClassification;
        List<String> compatibleLegacyKeys = type == TaskType.REQUIREMENT_ANALYSIS
                ? List.of(previousIdempotencyKey, "ticket:" + body.ticketId() + ":" + body.targetCommit())
                : List.of(previousIdempotencyKey);
        WorkflowTask task = tasks.createTaskWithLegacyKeys(taskId, type, scope, idempotencyKey, compatibleLegacyKeys,
                evidenceClassification, user.actorId(), CorrelationIdFilter.from(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowTaskResponse.from(task));
    }

    @GetMapping("/tasks")
    List<WorkflowTaskResponse> list(HttpServletRequest request) {
        CurrentUser.require(request);
        return tasks.listTasks().stream().map(WorkflowTaskResponse::from).toList();
    }

    @GetMapping("/tasks/{taskId}")
    WorkflowTaskResponse get(@PathVariable String taskId, HttpServletRequest request) {
        CurrentUser.require(request);
        return WorkflowTaskResponse.from(tasks.getTask(taskId));
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
        synchronized (tasks) {
            WorkflowTask task = tasks.getTask(taskId);
            TaskArtifactPolicy.requireCompatible(task.type(), body.type());
            tasks.validateTransition(taskId, TaskStatus.LOCAL_COPILOT_RUNNING,
                    TaskStatus.WAITING_FOR_USER_CONFIRMATION, task.version());
            ArtifactMetadata artifact = artifacts.create(body.artifactId(), taskId, body.type(), body.sections(),
                    user.actorId(), body.contentHash());
            try {
                tasks.transition(taskId, TaskStatus.LOCAL_COPILOT_RUNNING, TaskStatus.WAITING_FOR_USER_CONFIRMATION,
                        task.version(), user.actorId(), CorrelationIdFilter.from(request));
                return artifact;
            } catch (RuntimeException exception) {
                artifacts.delete(artifact);
                throw exception;
            }
        }
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

    @PostMapping("/tasks/{taskId}/ci")
    WorkflowTaskResponse recordCi(
            @PathVariable String taskId,
            @Valid @RequestBody CiResultRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        WorkflowTask task = tasks.getTask(taskId);
        requireClassificationActor(task.evidenceClassification(), user);
        requireMatchingPassClassification(task.evidenceClassification(), body.state() == CiResult.PASSED,
                body.state() == CiResult.SIMULATED_PASS);
        if (body.state() != CiResult.PASSED && body.state() != CiResult.SIMULATED_PASS) {
            return WorkflowTaskResponse.from(tasks.transition(taskId, TaskStatus.WAITING_FOR_CI,
                    TaskStatus.BLOCKED, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request)));
        }
        return WorkflowTaskResponse.from(tasks.transitionAfterPassedCi(
                taskId, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request)));
    }

    @PostMapping("/tasks/{taskId}/manual-e2e")
    WorkflowTaskResponse recordManualE2e(
            @PathVariable String taskId,
            @Valid @RequestBody ManualE2eRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        WorkflowTask task = tasks.getTask(taskId);
        requireClassificationActor(task.evidenceClassification(), user);
        requireMatchingPassClassification(task.evidenceClassification(), body.result() == ManualResult.PASS,
                body.result() == ManualResult.SIMULATED_PASS);
        validateManualResult(body, user);
        TaskStatus target = switch (body.result()) {
            case PASS, SIMULATED_PASS -> TaskStatus.COMPLETED;
            case FAIL, BLOCKED -> TaskStatus.BLOCKED;
        };
        return WorkflowTaskResponse.from(tasks.transition(taskId, TaskStatus.WAITING_FOR_MANUAL_E2E,
                target, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request)));
    }

    @PostMapping("/tasks/{taskId}/compatibility-complete")
    WorkflowTaskResponse completeLegacyStage(
            @PathVariable String taskId,
            @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        requireClassificationActor(tasks.getTask(taskId).evidenceClassification(), user);
        return WorkflowTaskResponse.from(tasks.completeLegacyApprovalOnlyTask(
                taskId, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request)));
    }

    @GetMapping("/tasks/{taskId}/audit")
    List<AuditEvent> audit(@PathVariable String taskId, HttpServletRequest request) {
        CurrentUser.require(request);
        return tasks.listAuditEvents(taskId);
    }

    @GetMapping("/tasks/{taskId}/audit/diagnostic")
    List<AuditEventDiagnostic> auditDiagnostic(@PathVariable String taskId, HttpServletRequest request) {
        CurrentUser.require(request);
        return tasks.listAuditEventsDiagnostic(taskId);
    }

    @PostMapping("/approvals")
    WorkflowTaskResponse approve(
            @Valid @RequestBody ApprovalRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        requireClassificationActor(tasks.getTask(body.taskId()).evidenceClassification(), user);
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
            @NotBlank String targetCommit,
            TaskType type,
            EvidenceClassification evidenceClassification) {
    }

    public record ClaimTaskRequest(long expectedVersion, @Min(1) @Max(120) int leaseMinutes) {
    }

    public record VersionRequest(long expectedVersion) {
    }

    public enum CiResult { PASSED, SIMULATED_PASS, FAILED, PENDING }

    public record CiResultRequest(
            @Min(0) long expectedVersion,
            @NotNull CiResult state,
            @NotBlank String buildFingerprint) {
    }

    public enum ManualResult { PASS, SIMULATED_PASS, FAIL, BLOCKED }

    public record ManualE2eRequest(
            @Min(0) long expectedVersion,
            String caseId,
            @NotNull ManualResult result,
            @NotBlank String actorRole,
            Instant executedAt,
            String buildFingerprint,
            String actualResult,
            String evidenceOrWaiver) {
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

    private static void validateManualResult(ManualE2eRequest body, CurrentUser user) {
        if (body.result() == ManualResult.SIMULATED_PASS) {
            if (!isSimulatedActor(user) || !"SIMULATED_RUNNER".equals(body.actorRole())
                    || body.executedAt() != null || hasText(body.caseId()) || hasText(body.buildFingerprint())
                    || hasText(body.actualResult()) || hasText(body.evidenceOrWaiver())) {
                throw new IllegalArgumentException(
                        "SIMULATED_PASS must not claim QA execution or manual evidence");
            }
            return;
        }
        if (!hasText(body.caseId()) || body.executedAt() == null || !hasText(body.buildFingerprint())
                || !hasText(body.actualResult()) || !hasText(body.evidenceOrWaiver())) {
            throw new IllegalArgumentException("Manual E2E results require execution evidence");
        }
    }

    private static boolean isSimulatedActor(CurrentUser user) {
        return user.actorId().startsWith("SIMULATED-");
    }

    private static void requireClassificationActor(EvidenceClassification classification, CurrentUser user) {
        boolean simulatedClassification = classification == EvidenceClassification.SIMULATED_PASS;
        if (simulatedClassification != isSimulatedActor(user)) {
            throw new IllegalArgumentException("Actor must match the workflow task evidence classification");
        }
    }

    private EvidenceClassification classificationFor(String ticketId, EvidenceClassification requested) {
        var ticket = tickets.findTicket(ticketId).orElse(null);
        if (ticket == null) {
            if (requested == EvidenceClassification.SIMULATED_PASS) {
                throw new IllegalArgumentException("SIMULATED_PASS tasks require an existing classified ticket");
            }
            return EvidenceClassification.REAL;
        }
        if (requested != null && requested != ticket.evidenceClassification()) {
            throw new IllegalArgumentException("Task evidence classification must match its ticket");
        }
        return ticket.evidenceClassification();
    }

    private static void requireMatchingPassClassification(EvidenceClassification classification,
            boolean realPass, boolean simulatedPass) {
        if ((simulatedPass && classification != EvidenceClassification.SIMULATED_PASS)
                || (realPass && classification == EvidenceClassification.SIMULATED_PASS)) {
            throw new IllegalArgumentException("Result must match the task evidence classification");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
