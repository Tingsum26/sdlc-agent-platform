package dev.sdlc.workflow.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.assignment.AssignmentReason;
import dev.sdlc.workflow.assignment.TaskAssignment;
import dev.sdlc.workflow.pod.PodMembership;
import dev.sdlc.workflow.pod.PodRoster;
import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.webhook.WebhookDelivery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MongoDocumentMappingTest {
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    void roundTripsAllSixMongoAggregatesWithoutLosingVersionOrHash() {
        WorkflowTask task = new WorkflowTask("TASK-1", TaskType.DESIGN, TaskStatus.WAITING_FOR_APPROVAL,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "idem-1", "PRINCIPAL-1", null, 7, NOW, NOW);
        AuditEvent audit = new AuditEvent("AUDIT-1", "TASK-1", 2, "PRINCIPAL-1", "APPROVED",
                TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.WAITING_FOR_CI, 7, NOW, "corr-1");
        ArtifactMetadata artifact = new ArtifactMetadata("ART-1", "TASK-1", ArtifactType.DESIGN_REPORT, 3,
                "sha256:fictional", List.of(new ArtifactSection("summary", "Summary", "Safe body")),
                "PRINCIPAL-1", NOW, null, null);
        WebhookDelivery webhook = new WebhookDelivery("DELIVERY-1", "pull_request", NOW);
        PodRoster roster = new PodRoster("ACCOUNT_OPENING_DEMO", 4, List.of(new PodMembership(
                "MEM-1", "EMP-1", "PRINCIPAL-1", "Fictional QA", "QA", "ACCOUNT_OPENING_DEMO",
                true, LocalDate.parse("2026-01-01"), null, List.of("qa-demo"))), NOW);
        TaskAssignment assignment = new TaskAssignment("DEMO-123", "ACCOUNT_OPENING_DEMO", "QA",
                "PRINCIPAL-1", AssignmentReason.POD_ROLE_MATCH, 2, NOW);

        assertEquals(task, WorkflowTaskDocument.fromDomain(task).toDomain());
        assertEquals(audit, AuditEventDocument.fromDomain(audit).toDomain());
        assertEquals(artifact, ArtifactDocument.fromDomain(artifact).toDomain());
        assertEquals(webhook, WebhookDeliveryDocument.fromDomain(webhook).toDomain());
        assertEquals(roster, PodRosterDocument.fromDomain(roster).toDomain());
        assertEquals(assignment, TaskAssignmentDocument.fromDomain(assignment).toDomain());
        assertEquals("ART-1:3", ArtifactDocument.fromDomain(artifact).id());
    }
}
