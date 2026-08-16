package dev.sdlc.workflow.api;

import dev.sdlc.workflow.assignment.AssignmentService;
import dev.sdlc.workflow.assignment.TaskAssignment;
import dev.sdlc.workflow.assignment.TaskAssignmentRepository;
import dev.sdlc.workflow.identity.EnterprisePrincipal;
import dev.sdlc.workflow.identity.IdentityBindingService;
import dev.sdlc.workflow.identity.IdentityNotFoundException;
import dev.sdlc.workflow.integration.IntegrationDiagnostic;
import dev.sdlc.workflow.integration.IntegrationDiagnosticService;
import dev.sdlc.workflow.pod.PodMembership;
import dev.sdlc.workflow.pod.PodRoster;
import dev.sdlc.workflow.pod.PodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterService;
import dev.sdlc.workflow.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal-readiness")
public class InternalReadinessController {
    private final IdentityBindingService identities;
    private final PodRosterService podService;
    private final PodRosterRepository podRosters;
    private final AssignmentService assignmentService;
    private final TaskAssignmentRepository assignments;
    private final IntegrationDiagnosticService diagnostics;

    public InternalReadinessController(
            IdentityBindingService identities,
            PodRosterService podService,
            PodRosterRepository podRosters,
            AssignmentService assignmentService,
            TaskAssignmentRepository assignments,
            IntegrationDiagnosticService diagnostics) {
        this.identities = identities;
        this.podService = podService;
        this.podRosters = podRosters;
        this.assignmentService = assignmentService;
        this.assignments = assignments;
        this.diagnostics = diagnostics;
    }

    @GetMapping("/identity")
    EnterprisePrincipal identity(HttpServletRequest request) {
        String principalId = CurrentUser.require(request).actorId();
        return identities.findByPrincipalId(principalId).orElseThrow(IdentityNotFoundException::new);
    }

    @GetMapping("/pods/{journeyId}")
    PodRoster pod(@PathVariable String journeyId, HttpServletRequest request) {
        CurrentUser.require(request);
        return podRosters.find(journeyId).orElseThrow(() -> new IllegalArgumentException("Pod roster not found"));
    }

    @PostMapping("/pods/import")
    PodRoster importPod(@Valid @RequestBody ImportPodRequest body, HttpServletRequest request) {
        String principalId = CurrentUser.require(request).actorId();
        return podService.importRoster(body.journeyId(), body.expectedRevision(), body.memberships(),
                principalId, CorrelationIdFilter.from(request));
    }

    @PostMapping("/pods/validate")
    java.util.Map<String, Object> validatePod(@Valid @RequestBody ImportPodRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        podService.validateRoster(body.journeyId(), body.memberships());
        return java.util.Map.of("valid", true, "journeyId", body.journeyId(), "rowCount", body.memberships().size());
    }

    @PostMapping("/assignments")
    TaskAssignment assign(@Valid @RequestBody AssignmentRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        return assignmentService.assign(body.ticketId(), body.journeyId(), body.requiredRole(), body.explicitPrincipalId());
    }

    @GetMapping("/assignments/{ticketId}")
    TaskAssignment assignment(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentUser.require(request);
        return assignments.find(ticketId).orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
    }

    @GetMapping("/integrations")
    List<IntegrationDiagnostic> integrations(HttpServletRequest request) {
        CurrentUser.require(request);
        return diagnostics.diagnostics();
    }

    @GetMapping("/next-validation")
    java.util.Map<String, Object> nextValidation(HttpServletRequest request) {
        CurrentUser.require(request);
        IntegrationDiagnostic next = diagnostics.diagnostics().stream()
                .filter(item -> item.status() != dev.sdlc.workflow.evidence.EvidenceStatus.CONTRACT_PASS)
                .findFirst().orElse(null);
        return next == null
                ? java.util.Map.of("complete", true)
                : java.util.Map.of("complete", false, "provider", next.provider(), "status", next.status(),
                        "instruction", "Run this provider check on the company network and attach sanitized evidence.");
    }

    public record ImportPodRequest(
            @NotBlank String journeyId,
            @Min(0) long expectedRevision,
            @NotNull List<PodMembership> memberships) {
        public ImportPodRequest {
            memberships = memberships == null ? List.of() : List.copyOf(memberships);
        }
    }

    public record AssignmentRequest(
            @NotBlank String ticketId,
            @NotBlank String journeyId,
            @NotBlank String requiredRole,
            String explicitPrincipalId) {
    }
}
