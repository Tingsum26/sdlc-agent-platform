package dev.sdlc.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactStore;
import dev.sdlc.workflow.artifact.JiraProjectionClient;
import dev.sdlc.workflow.artifact.MongoDocumentArtifactStore;
import dev.sdlc.workflow.assignment.AssignmentService;
import dev.sdlc.workflow.assignment.TaskAssignmentRepository;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.change.ChangeRequestRepository;
import dev.sdlc.workflow.change.ChangeRequestService;
import dev.sdlc.workflow.change.InMemoryChangeRequestRepository;
import dev.sdlc.workflow.dependency.DependencyRepository;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.enterprise.DeterministicFakeTransport;
import dev.sdlc.workflow.enterprise.EnterpriseTransport;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.identity.DirectoryPersonService;
import dev.sdlc.workflow.identity.EnrollmentCodeService;
import dev.sdlc.workflow.identity.IdentityBindingService;
import dev.sdlc.workflow.integration.CiStatusAdapter;
import dev.sdlc.workflow.integration.IntegrationDiagnosticService;
import dev.sdlc.workflow.integration.MockCiStatusAdapter;
import dev.sdlc.workflow.integration.SplunkDiagnosticAdapter;
import dev.sdlc.workflow.jiraprojection.FakeJiraProjectionClient;
import dev.sdlc.workflow.jiraprojection.InMemoryJiraProjectionRepository;
import dev.sdlc.workflow.jiraprojection.JiraProjectionRepository;
import dev.sdlc.workflow.jiraprojection.JiraProjectionService;
import dev.sdlc.workflow.persistence.MongoAuditEventRepository;
import dev.sdlc.workflow.persistence.MongoPodRosterRepository;
import dev.sdlc.workflow.persistence.MongoTaskAssignmentRepository;
import dev.sdlc.workflow.persistence.MongoWebhookDeliveryRepository;
import dev.sdlc.workflow.persistence.MongoWorkflowTaskRepository;
import dev.sdlc.workflow.pod.PodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterService;
import dev.sdlc.workflow.repotask.InMemoryRepoTaskRepository;
import dev.sdlc.workflow.repotask.RepoTaskRepository;
import dev.sdlc.workflow.repotask.RepoTaskService;
import dev.sdlc.workflow.skip.InMemorySkipAttestationRepository;
import dev.sdlc.workflow.skip.SkipAttestationRepository;
import dev.sdlc.workflow.skip.SkipService;
import dev.sdlc.workflow.splunk.SplunkAuditPublisher;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import dev.sdlc.workflow.task.WorkflowTaskService;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import dev.sdlc.workflow.webhook.WebhookDeliveryRepository;
import dev.sdlc.workflow.webhook.WebhookSignatureVerifier;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoOperations;

@Configuration
@Profile("mongo")
public class MongoRuntimeConfiguration {

    @Bean
    Clock workflowClock() { return Clock.systemUTC(); }

    @Bean
    WorkflowTaskRepository workflowTaskRepository(MongoOperations mongo) {
        return new MongoWorkflowTaskRepository(mongo);
    }

    @Bean
    AuditEventRepository auditEventRepository(MongoOperations mongo) {
        return new MongoAuditEventRepository(mongo);
    }

    @Bean
    WebhookDeliveryRepository webhookDeliveryRepository(MongoOperations mongo) {
        return new MongoWebhookDeliveryRepository(mongo);
    }

    @Bean
    PodRosterRepository podRosterRepository(MongoOperations mongo) {
        return new MongoPodRosterRepository(mongo);
    }

    @Bean
    TaskAssignmentRepository taskAssignmentRepository(MongoOperations mongo) {
        return new MongoTaskAssignmentRepository(mongo);
    }

    @Bean
    ArtifactStore artifactStore(MongoOperations mongo) { return new MongoDocumentArtifactStore(mongo); }

    @Bean
    TaskTransitionPolicy taskTransitionPolicy() { return new TaskTransitionPolicy(); }

    @Bean
    WorkflowTaskService workflowTaskService(
            WorkflowTaskRepository tasks, AuditEventRepository audits, TaskTransitionPolicy policy, Clock clock) {
        return new WorkflowTaskService(tasks, audits, policy, clock);
    }

    @Bean
    ArtifactService artifactService(ArtifactStore store, ObjectMapper mapper, Clock clock) {
        return new ArtifactService(store, mapper, clock);
    }

    @Bean
    ApprovalService approvalService(WorkflowTaskService tasks, ArtifactService artifacts) {
        return new ApprovalService(tasks, artifacts);
    }

    @Bean
    DirectoryPersonService directoryPersonService(Clock clock) { return new DirectoryPersonService(clock); }

    @Bean
    IdentityBindingService identityBindingService() { return new IdentityBindingService(); }

    @Bean
    EnrollmentCodeService enrollmentCodeService(IdentityBindingService bindings, Clock clock) {
        return new EnrollmentCodeService(bindings, clock);
    }

    @Bean
    PodRosterService podRosterService(PodRosterRepository rosters, AuditEventRepository audits, Clock clock) {
        return new PodRosterService(rosters, audits, clock);
    }

    @Bean
    AssignmentService assignmentService(
            PodRosterRepository rosters, TaskAssignmentRepository assignments, Clock clock) {
        return new AssignmentService(rosters, assignments, clock);
    }

    @Bean
    WebhookSignatureVerifier webhookSignatureVerifier(@Value("${workflow.webhook.secret}") String secret) {
        return new WebhookSignatureVerifier(secret);
    }

    @Bean
    IntegrationDiagnosticService integrationDiagnosticService(Clock clock) {
        return new IntegrationDiagnosticService(clock, false);
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

    @Bean
    EnterpriseTransport enterpriseTransport() {
        // TODO(INTERNAL): INTERNAL-SPLUNK-001 Point the Splunk audit publisher at the real HEC endpoint.
        return new DeterministicFakeTransport();
    }

    @Bean
    SplunkDiagnosticAdapter splunkDiagnosticAdapter(EnterpriseTransport transport, ObjectMapper objectMapper,
            Clock clock) {
        return new SplunkDiagnosticAdapter(transport, objectMapper, clock);
    }

    @Bean
    SplunkAuditPublisher splunkAuditPublisher(SplunkDiagnosticAdapter splunk) {
        return new SplunkAuditPublisher(splunk);
    }
}
