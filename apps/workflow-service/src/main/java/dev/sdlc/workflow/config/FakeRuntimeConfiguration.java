package dev.sdlc.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.assignment.AssignmentService;
import dev.sdlc.workflow.assignment.InMemoryTaskAssignmentRepository;
import dev.sdlc.workflow.assignment.TaskAssignmentRepository;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactStore;
import dev.sdlc.workflow.artifact.FakeArtifactStore;
import dev.sdlc.workflow.artifact.JiraProjectionClient;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.change.ChangeRequestRepository;
import dev.sdlc.workflow.change.ChangeRequestService;
import dev.sdlc.workflow.change.InMemoryChangeRequestRepository;
import dev.sdlc.workflow.dependency.DependencyRepository;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.identity.DirectoryPersonService;
import dev.sdlc.workflow.identity.EnrollmentCodeService;
import dev.sdlc.workflow.identity.IdentityBindingService;
import dev.sdlc.workflow.identity.OnboardingStatus;
import dev.sdlc.workflow.integration.CiStatusAdapter;
import dev.sdlc.workflow.integration.IntegrationDiagnosticService;
import dev.sdlc.workflow.integration.MockCiStatusAdapter;
import dev.sdlc.workflow.jiraprojection.FakeJiraProjectionClient;
import dev.sdlc.workflow.jiraprojection.InMemoryJiraProjectionRepository;
import dev.sdlc.workflow.jiraprojection.JiraProjectionRepository;
import dev.sdlc.workflow.jiraprojection.JiraProjectionService;
import dev.sdlc.workflow.pod.InMemoryPodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterService;
import dev.sdlc.workflow.repotask.InMemoryRepoTaskRepository;
import dev.sdlc.workflow.repotask.RepoTaskRepository;
import dev.sdlc.workflow.repotask.RepoTaskService;
import dev.sdlc.workflow.skip.InMemorySkipAttestationRepository;
import dev.sdlc.workflow.skip.SkipAttestationRepository;
import dev.sdlc.workflow.skip.SkipService;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import dev.sdlc.workflow.task.WorkflowTaskService;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import dev.sdlc.workflow.webhook.InMemoryWebhookDeliveryRepository;
import dev.sdlc.workflow.webhook.WebhookDeliveryRepository;
import dev.sdlc.workflow.webhook.WebhookSignatureVerifier;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("fake")
public class FakeRuntimeConfiguration {

    @Bean
    Clock workflowClock() {
        return Clock.systemUTC();
    }

    @Bean
    WorkflowTaskRepository workflowTaskRepository() {
        return new InMemoryWorkflowTaskRepository();
    }

    @Bean
    AuditEventRepository auditEventRepository() {
        return new InMemoryAuditEventRepository();
    }

    @Bean
    TaskTransitionPolicy taskTransitionPolicy() {
        return new TaskTransitionPolicy();
    }

    @Bean
    WorkflowTaskService workflowTaskService(
            WorkflowTaskRepository tasks,
            AuditEventRepository auditEvents,
            TaskTransitionPolicy policy,
            Clock clock) {
        return new WorkflowTaskService(tasks, auditEvents, policy, clock);
    }

    @Bean
    ArtifactStore artifactStore() {
        return new FakeArtifactStore();
    }

    @Bean
    ArtifactService artifactService(ArtifactStore artifactStore, ObjectMapper objectMapper, Clock clock) {
        return new ArtifactService(artifactStore, objectMapper, clock);
    }

    @Bean
    ApprovalService approvalService(WorkflowTaskService tasks, ArtifactService artifacts) {
        return new ApprovalService(tasks, artifacts);
    }

    @Bean
    WebhookDeliveryRepository webhookDeliveryRepository() {
        return new InMemoryWebhookDeliveryRepository();
    }

    @Bean
    WebhookSignatureVerifier webhookSignatureVerifier(
            @Value("${workflow.webhook.secret:fictional-local-webhook-secret}") String secret) {
        return new WebhookSignatureVerifier(secret);
    }

    @Bean
    DirectoryPersonService directoryPersonService(Clock clock) {
        return new DirectoryPersonService(clock);
    }

    @Bean
    IdentityBindingService identityBindingService(DirectoryPersonService directory) {
        IdentityBindingService service = new IdentityBindingService();
        dev.sdlc.workflow.identity.EnterprisePrincipal sm =
                service.bindAdminPrincipal("EMP-100", "Fictional Scrum Master", "f***@example.invalid");
        // TODO(INTERNAL): INTERNAL-IDN-002 Seed real admin-principal provisioning instead of the fictional EMP-100.
        directory.upsert(sm.principalId(), sm.employeeId(), sm.displayLabel(), OnboardingStatus.ONBOARDED);
        return service;
    }

    @Bean
    EnrollmentCodeService enrollmentCodeService(IdentityBindingService bindings, Clock clock) {
        return new EnrollmentCodeService(bindings, clock);
    }

    @Bean
    PodRosterRepository podRosterRepository() {
        return new InMemoryPodRosterRepository();
    }

    @Bean
    PodRosterService podRosterService(PodRosterRepository rosters, AuditEventRepository audits, Clock clock) {
        return new PodRosterService(rosters, audits, clock);
    }

    @Bean
    TaskAssignmentRepository taskAssignmentRepository() {
        return new InMemoryTaskAssignmentRepository();
    }

    @Bean
    AssignmentService assignmentService(
            PodRosterRepository rosters, TaskAssignmentRepository assignments, Clock clock) {
        return new AssignmentService(rosters, assignments, clock);
    }

    @Bean
    IntegrationDiagnosticService integrationDiagnosticService(Clock clock) {
        return new IntegrationDiagnosticService(clock, true);
    }

    @Bean
    DomainAuditEventRepository domainAuditEventRepository() {
        // TODO(INTERNAL): INTERNAL-AUD-001 Persist M2 domain aggregates and audit events to MongoDB.
        return new InMemoryDomainAuditEventRepository();
    }

    @Bean
    EpicWorkflowRepository epicWorkflowRepository() {
        return new InMemoryEpicWorkflowRepository();
    }

    @Bean
    TicketWorkflowRepository ticketWorkflowRepository() {
        return new InMemoryTicketWorkflowRepository();
    }

    @Bean
    RepoTaskRepository repoTaskRepository() {
        return new InMemoryRepoTaskRepository();
    }

    @Bean
    DependencyRepository dependencyRepository() {
        return new InMemoryDependencyRepository();
    }

    @Bean
    ChangeRequestRepository changeRequestRepository() {
        return new InMemoryChangeRequestRepository();
    }

    @Bean
    SkipAttestationRepository skipAttestationRepository() {
        return new InMemorySkipAttestationRepository();
    }

    @Bean
    EpicWorkflowService epicWorkflowService(EpicWorkflowRepository epics, DomainAuditEventRepository audits,
            Clock clock) {
        return new EpicWorkflowService(epics, audits, clock);
    }

    @Bean
    TicketWorkflowService ticketWorkflowService(EpicWorkflowRepository epics, TicketWorkflowRepository tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        return new TicketWorkflowService(epics, tickets, dependencies, audits, clock);
    }

    @Bean
    RepoTaskService repoTaskService(TicketWorkflowService tickets, RepoTaskRepository repoTasks,
            DomainAuditEventRepository audits, Clock clock) {
        return new RepoTaskService(tickets, repoTasks, audits, clock);
    }

    @Bean
    DependencyService dependencyService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        return new DependencyService(epics, tickets, dependencies, audits, clock);
    }

    @Bean
    ChangeRequestService changeRequestService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            ChangeRequestRepository requests, DomainAuditEventRepository audits, Clock clock) {
        return new ChangeRequestService(epics, tickets, requests, audits, clock);
    }

    @Bean
    SkipService skipService(WorkflowTaskService workflowTasks, SkipAttestationRepository attestations, Clock clock) {
        return new SkipService(workflowTasks, attestations, clock);
    }

    @Bean
    JiraProjectionRepository jiraProjectionRepository() {
        return new InMemoryJiraProjectionRepository();
    }

    @Bean
    JiraProjectionClient jiraProjectionClient() {
        // TODO(INTERNAL): INTERNAL-JIRA-001 Replace the fake projection client with the real Jira comment API.
        return new FakeJiraProjectionClient();
    }

    @Bean
    JiraProjectionService jiraProjectionService(JiraProjectionRepository projections,
            JiraProjectionClient client, Clock clock) {
        return new JiraProjectionService(projections, client, clock);
    }

    @Bean
    CiStatusAdapter ciStatusAdapter() {
        // TODO(INTERNAL): INTERNAL-CI-001 Replace the mock CI adapter with the real Jenkins adapter.
        return new MockCiStatusAdapter();
    }
}
