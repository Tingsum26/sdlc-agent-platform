package dev.sdlc.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.approval.ApprovalService;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactStore;
import dev.sdlc.workflow.artifact.MongoDocumentArtifactStore;
import dev.sdlc.workflow.assignment.AssignmentService;
import dev.sdlc.workflow.assignment.TaskAssignmentRepository;
import dev.sdlc.workflow.identity.IdentityBindingService;
import dev.sdlc.workflow.persistence.MongoAuditEventRepository;
import dev.sdlc.workflow.persistence.MongoPodRosterRepository;
import dev.sdlc.workflow.persistence.MongoTaskAssignmentRepository;
import dev.sdlc.workflow.persistence.MongoWebhookDeliveryRepository;
import dev.sdlc.workflow.persistence.MongoWorkflowTaskRepository;
import dev.sdlc.workflow.pod.PodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterService;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import dev.sdlc.workflow.task.WorkflowTaskService;
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
    IdentityBindingService identityBindingService() { return new IdentityBindingService(); }

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
}
