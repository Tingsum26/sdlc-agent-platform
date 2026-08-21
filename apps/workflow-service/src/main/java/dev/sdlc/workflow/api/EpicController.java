package dev.sdlc.workflow.api;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.change.ChangeRequestService;
import dev.sdlc.workflow.change.ChangeUrgency;
import dev.sdlc.workflow.change.EpicChangeRequest;
import dev.sdlc.workflow.dependency.Dependency;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.evidence.EvidenceClassification;
import dev.sdlc.workflow.epic.EpicWorkflow;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.integration.CiState;
import dev.sdlc.workflow.integration.CiStatus;
import dev.sdlc.workflow.integration.CiStatusAdapter;
import dev.sdlc.workflow.jiraprojection.JiraProjection;
import dev.sdlc.workflow.jiraprojection.JiraProjectionService;
import dev.sdlc.workflow.jiraprojection.JiraSummaryFactory;
import dev.sdlc.workflow.repotask.RepoTask;
import dev.sdlc.workflow.repotask.RepoTaskService;
import dev.sdlc.workflow.repotask.RepoTaskStatus;
import dev.sdlc.workflow.security.CurrentUser;
import dev.sdlc.workflow.skip.SkipAttestation;
import dev.sdlc.workflow.skip.SkipResult;
import dev.sdlc.workflow.skip.SkipService;
import dev.sdlc.workflow.splunk.SplunkAuditPublisher;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import dev.sdlc.workflow.ticket.TicketDeliveryStatus;
import dev.sdlc.workflow.ticket.TicketWorkflow;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EpicController {

    private final EpicWorkflowService epics;
    private final TicketWorkflowService tickets;
    private final RepoTaskService repoTasks;
    private final DependencyService dependencies;
    private final ChangeRequestService changeRequests;
    private final SkipService skips;
    private final WorkflowTaskService workflowTasks;
    private final DomainAuditEventRepository audits;
    private final JiraProjectionService jiraProjections;
    private final ArtifactService artifacts;
    private final JiraSummaryFactory jiraSummaries = new JiraSummaryFactory();
    private final CiStatusAdapter ciStatusAdapter;
    private final SplunkAuditPublisher splunkAudit;

    public EpicController(EpicWorkflowService epics, TicketWorkflowService tickets, RepoTaskService repoTasks,
            DependencyService dependencies, ChangeRequestService changeRequests, SkipService skips,
            WorkflowTaskService workflowTasks, DomainAuditEventRepository audits,
            JiraProjectionService jiraProjections, ArtifactService artifacts, CiStatusAdapter ciStatusAdapter,
            SplunkAuditPublisher splunkAudit) {
        this.epics = epics;
        this.tickets = tickets;
        this.repoTasks = repoTasks;
        this.dependencies = dependencies;
        this.changeRequests = changeRequests;
        this.skips = skips;
        this.workflowTasks = workflowTasks;
        this.audits = audits;
        this.jiraProjections = jiraProjections;
        this.artifacts = artifacts;
        this.ciStatusAdapter = ciStatusAdapter;
        this.splunkAudit = splunkAudit;
    }

    @PostMapping("/epics")
    ResponseEntity<EpicWorkflow> createEpic(@Valid @RequestBody EpicRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-EPIC-001 Sync Epic creation and Ticket status changes with the company Jira.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(epics.create(body.epicId(), body.title(), body.journeyId(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @PostMapping("/epics/{epicId}/activate")
    EpicWorkflow activate(@PathVariable String epicId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return epics.activate(epicId, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request));
    }

    @GetMapping("/epics")
    List<EpicWorkflow> epics(HttpServletRequest request) {
        CurrentUser.require(request);
        return epics.list();
    }

    @GetMapping("/epics/{epicId}")
    EpicWorkflow epic(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return epics.get(epicId);
    }

    @PostMapping("/epics/{epicId}/tickets")
    ResponseEntity<TicketWorkflow> attachTicket(@PathVariable String epicId,
            @Valid @RequestBody AttachTicketRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        EvidenceClassification evidenceClassification = body.evidenceClassification() == null
                ? EvidenceClassification.REAL : body.evidenceClassification();
        if (evidenceClassification == EvidenceClassification.SIMULATED_PASS
                && !user.actorId().startsWith("SIMULATED-")) {
            throw new IllegalArgumentException("SIMULATED_PASS classification requires a simulated actor");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tickets.create(epicId, body.ticketId(), body.channel(), evidenceClassification, user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @GetMapping("/epics/{epicId}/tickets")
    List<TicketWorkflow> tickets(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return tickets.listByEpic(epicId);
    }

    @PostMapping("/tickets/{ticketId}/advance")
    TicketWorkflow advance(@PathVariable String ticketId, @Valid @RequestBody AdvanceRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return tickets.transition(ticketId, body.expectedVersion(), body.target(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/tickets/{ticketId}/ack-change")
    TicketWorkflow ackChange(@PathVariable String ticketId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return tickets.ackChange(ticketId, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request));
    }

    @PostMapping("/jira-drafts")
    ResponseEntity<JiraProjection> createJiraDraft(@Valid @RequestBody JiraProjectionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        TicketWorkflow ticket = tickets.ticket(body.ticketId());
        ArtifactMetadata artifact = artifacts.requireApprovedForProjection(body.artifactId(), body.artifactVersion());
        WorkflowTask task = workflowTasks.requireCommittedApproval(
                artifact.taskId(), artifact.approvedTaskVersion(), artifact.artifactId(), artifact.version(),
                artifact.approvalCommitEventId());
        if (!ticket.ticketId().equals(task.scope().ticketId())) {
            throw new IllegalArgumentException("Artifact task does not belong to ticket");
        }
        // TODO(INTERNAL): INTERNAL-JIRA-001 Route the projection outbox to the real Jira comment API.
        return ResponseEntity.status(HttpStatus.CREATED).body(jiraProjections.enqueue(ticket.ticketId(), body.milestoneId(),
                        jiraSummaries.create(ticket, artifact),
                        user.actorId(), CorrelationIdFilter.from(request)));
    }

    @PostMapping("/jira-drafts/{projectionId}/publish")
    JiraProjection publishJiraDraft(@PathVariable String projectionId, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        String correlationId = CorrelationIdFilter.from(request);
        JiraProjection updated = jiraProjections.flush(projectionId, user.actorId(), correlationId);
        splunkAudit.jiraProjection(updated.ticketId(), updated.milestoneId(), updated.status().name(),
                correlationId, "");
        return updated;
    }

    @PostMapping("/jira-drafts/retry")
    List<JiraProjection> retryJiraDrafts(HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        String correlationId = CorrelationIdFilter.from(request);
        List<JiraProjection> updated = jiraProjections.flushPending(user.actorId(), correlationId);
        for (JiraProjection projection : updated) {
            splunkAudit.jiraProjection(projection.ticketId(), projection.milestoneId(),
                    projection.status().name(), correlationId, "");
        }
        return updated;
    }

    @GetMapping("/jira-drafts/{projectionId}")
    JiraProjection jiraDraft(@PathVariable String projectionId, HttpServletRequest request) {
        CurrentUser.require(request);
        return jiraProjections.get(projectionId);
    }

    @PostMapping("/tickets/{ticketId}/ci")
    Map<String, Object> recordCi(@PathVariable String ticketId, @Valid @RequestBody CiRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-CI-001 Route CI status to the real Jenkins adapter; the fake profile uses the
        // mock PASSED adapter.
        CiStatus status = ciStatusAdapter.getStatus(body.repositoryAlias(), body.revision());
        TicketWorkflow ticket = tickets.ticket(ticketId);
        String correlationId = CorrelationIdFilter.from(request);
        TicketWorkflow advanced = tickets.transition(ticketId, ticket.version(),
                status.state() == CiState.PASSED ? TicketDeliveryStatus.CI_PASSED : TicketDeliveryStatus.BLOCKED,
                user.actorId(), correlationId);
        splunkAudit.ciStatus(ticketId, body.repositoryAlias(), status.state().name(), correlationId);
        return Map.of("ticket", advanced, "status", advanced.status().name(), "state", status.state().name(),
                "evidenceClassification", advanced.evidenceClassification().name(), "detailsUrl", status.detailsUrl());
    }

    @PostMapping("/tickets/{ticketId}/repo-tasks")
    ResponseEntity<RepoTask> addRepoTask(@PathVariable String ticketId, @Valid @RequestBody RepoTaskRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repoTasks.create(ticketId, body.repositoryAlias(), body.baseCommit(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @GetMapping("/tickets/{ticketId}/repo-tasks")
    List<RepoTask> repoTasks(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentUser.require(request);
        return repoTasks.listByTicket(ticketId);
    }

    @GetMapping("/tickets/{ticketId}/audit")
    List<DomainAuditEvent> ticketAudit(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentUser.require(request);
        tickets.ticket(ticketId);
        return audits.findByAggregateId(ticketId);
    }

    @PostMapping("/repo-tasks/{repoTaskId}/advance")
    RepoTask advanceRepoTask(@PathVariable String repoTaskId, @Valid @RequestBody RepoTaskAdvanceRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return repoTasks.transition(repoTaskId, body.expectedVersion(), body.target(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/epics/{epicId}/dependencies")
    ResponseEntity<Dependency> addDependency(@PathVariable String epicId, @Valid @RequestBody DependencyRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dependencies.add(epicId, body.fromTicketId(), body.toTicketId(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @PostMapping("/dependencies/{dependencyId}/resolve")
    Dependency resolveDependency(@PathVariable String dependencyId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return dependencies.resolve(dependencyId, body.expectedVersion(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @GetMapping("/epics/{epicId}/dependencies")
    List<Dependency> dependencies(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return dependencies.listByEpic(epicId);
    }

    @PostMapping("/epics/{epicId}/change-requests")
    ResponseEntity<EpicChangeRequest> createChangeRequest(@PathVariable String epicId,
            @Valid @RequestBody ChangeRequestRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(changeRequests.create(epicId, body.reason(), body.urgency(), body.description(),
                        body.affectedTicketIds(), user.actorId(), CorrelationIdFilter.from(request)));
    }

    @GetMapping("/epics/{epicId}/change-requests")
    List<EpicChangeRequest> changeRequests(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return changeRequests.listByEpic(epicId);
    }

    @PostMapping("/change-requests/{changeRequestId}/approve")
    EpicChangeRequest approveChangeRequest(@PathVariable String changeRequestId,
            @Valid @RequestBody ApproveRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return changeRequests.approve(changeRequestId, body.expectedVersion(), user.actorId(), body.actorRole(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/change-requests/{changeRequestId}/reject")
    EpicChangeRequest rejectChangeRequest(@PathVariable String changeRequestId,
            @Valid @RequestBody RejectRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return changeRequests.reject(changeRequestId, body.expectedVersion(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/tasks/{taskId}/skip")
    Map<String, Object> skipTask(@PathVariable String taskId, @Valid @RequestBody SkipRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        SkipResult result = skips.skip(taskId, body.expectedVersion(), body.reason(), body.discussedWith(),
                user.actorId(), body.actorRole(), CorrelationIdFilter.from(request));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task", WorkflowTaskResponse.from(result.task()));
        response.put("attestation", result.attestation());
        return response;
    }

    @GetMapping("/tasks/{taskId}/skips")
    List<SkipAttestation> skips(@PathVariable String taskId, HttpServletRequest request) {
        CurrentUser.require(request);
        return skips.listByTask(taskId);
    }

    @GetMapping("/epics/{epicId}/resume")
    Map<String, Object> resume(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        EpicWorkflow epic = epics.get(epicId);
        List<Map<String, Object>> ticketViews = tickets.listByEpic(epicId).stream().map(ticket -> {
            List<WorkflowTask> open = workflowTasks.listTasks().stream()
                    .filter(task -> task.scope().ticketId().equals(ticket.ticketId())
                            && task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.CANCELLED)
                    .toList();
            return Map.<String, Object>of("ticket", ticket, "openTasks", open,
                    "nextAction", nextActionFor(ticket, open));
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("epic", epic);
        result.put("tickets", ticketViews);
        result.put("auditTrail", audits.findByAggregateId(epicId));
        return result;
    }

    private static String nextActionFor(TicketWorkflow ticket, List<WorkflowTask> open) {
        if (!open.isEmpty()) {
            return "claim task " + open.get(0).taskId();
        }
        return switch (ticket.status()) {
            case PLANNED -> "start requirement analysis";
            case IN_ANALYSIS -> "submit requirement contract";
            case WAITING_FOR_APPROVAL -> "approve requirement";
            case IN_DEVELOPMENT -> "open PR";
            case PR_OPEN -> "record CI result";
            case CI_PASSED -> "merge after review";
            case MERGED -> "release";
            case RELEASED -> "enable feature flag";
            case FLAG_ENABLED -> "verify E2E";
            case BLOCKED -> "resolve blocker";
            case E2E_VERIFIED, CANCELLED -> "none";
        };
    }

    public record EpicRequest(@NotBlank String epicId, @NotBlank String title, @NotBlank String journeyId) {
    }

    public record VersionRequest(@Min(0) long expectedVersion) {
    }

    public record AttachTicketRequest(@NotBlank String ticketId, @NotNull Channel channel,
            EvidenceClassification evidenceClassification) {
    }

    public record AdvanceRequest(@Min(0) long expectedVersion, @NotNull TicketDeliveryStatus target) {
    }

    public record JiraProjectionRequest(@NotBlank String ticketId, @NotBlank String milestoneId,
            @NotBlank String artifactId, @Min(1) int artifactVersion) {
    }

    public record CiRequest(@NotBlank String repositoryAlias, @NotBlank String revision) {
    }

    public record RepoTaskRequest(@NotBlank String repositoryAlias, @NotBlank String baseCommit) {
    }

    public record RepoTaskAdvanceRequest(@Min(0) long expectedVersion, @NotNull RepoTaskStatus target) {
    }

    public record DependencyRequest(@NotBlank String fromTicketId, @NotBlank String toTicketId) {
    }

    public record ChangeRequestRequest(@NotBlank String reason, @NotNull ChangeUrgency urgency,
            @NotBlank String description, @NotNull List<String> affectedTicketIds) {
    }

    public record ApproveRequest(@Min(0) long expectedVersion, @NotBlank String actorRole) {
    }

    public record RejectRequest(@Min(0) long expectedVersion) {
    }

    public record SkipRequest(@Min(0) long expectedVersion, @NotBlank String reason, String discussedWith,
            @NotBlank String actorRole) {
    }
}
