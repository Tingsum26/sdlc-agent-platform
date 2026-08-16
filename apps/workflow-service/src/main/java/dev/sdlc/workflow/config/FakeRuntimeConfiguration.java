package dev.sdlc.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.assignment.AssignmentService;
import dev.sdlc.workflow.assignment.InMemoryTaskAssignmentRepository;
import dev.sdlc.workflow.assignment.TaskAssignmentRepository;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactStore;
import dev.sdlc.workflow.artifact.FakeArtifactStore;
import dev.sdlc.workflow.identity.IdentityBindingService;
import dev.sdlc.workflow.integration.IntegrationDiagnosticService;
import dev.sdlc.workflow.pod.InMemoryPodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterService;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import dev.sdlc.workflow.task.WorkflowTaskService;
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
    IdentityBindingService identityBindingService() {
        IdentityBindingService service = new IdentityBindingService();
        service.bindAdminPrincipal("EMP-100", "Fictional Scrum Master", "f***@example.invalid");
        return service;
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
}
