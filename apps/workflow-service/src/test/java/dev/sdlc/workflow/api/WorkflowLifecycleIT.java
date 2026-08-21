package dev.sdlc.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.artifact.ArtifactSection;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.ArtifactType;
import dev.sdlc.workflow.evidence.EvidenceClassification;
import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.AuditEventRepository;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fake")
class WorkflowLifecycleIT {
    @Autowired MockMvc mvc;
    @Autowired WorkflowTaskRepository taskRepository;
    @Autowired AuditEventRepository auditEvents;
    @Autowired ArtifactService artifacts;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void advancesFromMockCiThroughTraceableManualE2eAndExposesAuditHistory() throws Exception {
        JsonNode created = json(mvc.perform(post("/api/v1/workflows/from-ticket").header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"ticketId":"DEMO-LIFECYCLE","repositoryAlias":"REPO_A","targetCommit":"3123456789abcdef0123456789abcdef01234567","type":"MANUAL_E2E"}
                    """)).andReturn().getResponse().getContentAsString());
        String taskId = created.path("taskId").asText();

        mvc.perform(post("/api/v1/tasks/{id}/claim", taskId).header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"leaseMinutes\":15}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks/{id}/results", taskId).header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"artifactId":"ART-LIFECYCLE","type":"MANUAL_E2E_REPORT","sections":[{"key":"summary","title":"Summary","body":"Safe fictional evidence"}]}
                    """)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks/{id}/confirm", taskId).header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/approvals").header("X-Demo-User", "architect-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"taskId":"%s","artifactId":"ART-LIFECYCLE","artifactVersion":1,"expectedTaskVersion":3}
                    """.formatted(taskId))).andExpect(status().isOk());

        mvc.perform(post("/api/v1/tasks/{id}/ci", taskId).header("X-Demo-User", "ci-reader")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":4,\"state\":\"PASSED\",\"buildFingerprint\":\"REPO_A@3123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING_FOR_MANUAL_E2E"));

        mvc.perform(post("/api/v1/tasks/{id}/manual-e2e", taskId).header("X-Demo-User", "qa-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"expectedVersion":5,"caseId":"E2E-1","result":"PASS","actorRole":"QA","executedAt":"2026-08-16T08:00:00Z","buildFingerprint":"REPO_A@3123456","actualResult":"Confirmation shown","evidenceOrWaiver":"EVIDENCE-1"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/v1/tasks/{id}/audit", taskId).header("X-Demo-User", "developer-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].actorId").value("qa-1"));
    }

    @Test
    void normalAuditHidesInvalidatedSameVersionEventWhileDiagnosticIdentifiesIt() throws Exception {
        String taskId = "TASK-AUDIT-VISIBILITY";
        Instant occurredAt = Instant.parse("2026-08-16T08:00:00Z");
        taskRepository.save(new WorkflowTask(taskId, TaskType.IMPLEMENTATION, TaskStatus.COMPLETED,
                new WorkflowScope("AUDIT-VISIBILITY", "REPO_A", "audit-ref"),
                "audit-visibility", "developer-1", null, 5, occurredAt, occurredAt));
        AuditEvent invalidated = new AuditEvent("AUDIT-OLD-V5", taskId, 1, "old-worker", "TASK_TRANSITIONED",
                TaskStatus.WAITING_FOR_CI, TaskStatus.COMPLETED, 5, occurredAt, "corr-old");
        AuditEvent committed = new AuditEvent("AUDIT-VALID-V5", taskId, 2, "valid-worker", "TASK_TRANSITIONED",
                TaskStatus.WAITING_FOR_CI, TaskStatus.COMPLETED, 5, occurredAt.plusSeconds(1), "corr-valid");
        auditEvents.append(invalidated);
        auditEvents.invalidate(invalidated.eventId());
        auditEvents.append(committed);

        JsonNode normal = json(mvc.perform(get("/api/v1/tasks/{id}/audit", taskId)
                        .header("X-Demo-User", "developer-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(normal).hasSize(1);
        assertThat(normal.get(0).path("eventId").asText()).isEqualTo("AUDIT-VALID-V5");

        JsonNode diagnostic = json(mvc.perform(get("/api/v1/tasks/{id}/audit/diagnostic", taskId)
                        .header("X-Demo-User", "diagnostic-operator"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(diagnostic).hasSize(2);
        assertThat(diagnostic.get(0).path("event").path("eventId").asText()).isEqualTo("AUDIT-OLD-V5");
        assertThat(diagnostic.get(0).path("invalidated").asBoolean()).isTrue();
        assertThat(diagnostic.get(1).path("event").path("eventId").asText()).isEqualTo("AUDIT-VALID-V5");
        assertThat(diagnostic.get(1).path("invalidated").asBoolean()).isFalse();
    }

    @Test
    void rejectsManualPassWithoutEvidence() throws Exception {
        mvc.perform(post("/api/v1/tasks/TASK-MISSING/manual-e2e").header("X-Demo-User", "qa-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":5,\"caseId\":\"E2E-1\",\"result\":\"PASS\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = { "IMPLEMENTATION", "TEST_GENERATION", "PR_REVIEW" })
    void ciStagesCompleteAfterPassedCiWithoutEnteringManualE2e(TaskType taskType) throws Exception {
        String artifactType = switch (taskType) {
            case IMPLEMENTATION -> "DELIVERY_REPORT";
            case TEST_GENERATION -> "TEST_REPORT";
            case PR_REVIEW -> "PR_REVIEW_REPORT";
            default -> throw new IllegalArgumentException("Unexpected CI stage " + taskType);
        };
        JsonNode created = json(mvc.perform(post("/api/v1/workflows/from-ticket").header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"ticketId":"DEMO-%s","repositoryAlias":"REPO_A","targetCommit":"4123456789abcdef0123456789abcdef01234567","type":"%s"}
                    """.formatted(taskType, taskType))).andReturn().getResponse().getContentAsString());
        String taskId = created.path("taskId").asText();
        String artifactId = "ART-" + taskType;

        mvc.perform(post("/api/v1/tasks/{id}/claim", taskId).header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"leaseMinutes\":15}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks/{id}/results", taskId).header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"artifactId":"%s","type":"%s","sections":[{"key":"summary","title":"Summary","body":"Safe fictional CI-stage evidence"}]}
                    """.formatted(artifactId, artifactType))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks/{id}/confirm", taskId).header("X-Demo-User", "developer-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/approvals").header("X-Demo-User", "reviewer-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"taskId":"%s","artifactId":"%s","artifactVersion":1,"expectedTaskVersion":3}
                    """.formatted(taskId, artifactId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_CI"));

        mvc.perform(post("/api/v1/tasks/{id}/ci", taskId).header("X-Demo-User", "ci-reader")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":4,\"state\":\"PASSED\",\"buildFingerprint\":\"REPO_A@4123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(post("/api/v1/tasks/{id}/manual-e2e", taskId).header("X-Demo-User", "qa-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"expectedVersion":5,"caseId":"E2E-NOT-ALLOWED","result":"PASS","actorRole":"QA","executedAt":"2026-08-16T08:00:00Z","buildFingerprint":"REPO_A@4123456","actualResult":"Should not run","evidenceOrWaiver":"EVIDENCE-NOT-ALLOWED"}
                    """))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/tasks/{id}/audit", taskId).header("X-Demo-User", "developer-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[5].actorId").value("ci-reader"))
                .andExpect(jsonPath("$[5].newStatus").value("COMPLETED"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {
            "REQUIREMENT_ANALYSIS", "DESIGN", "DELIVERY_COORDINATION", "ONBOARDING_SYNC" })
    void explicitlyCompletesApprovalOnlyTasksStrandedOnTheLegacyManualGate(TaskType taskType) throws Exception {
        String taskId = "TASK-LEGACY-MANUAL-" + taskType;
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        taskRepository.save(new WorkflowTask(taskId, taskType, TaskStatus.WAITING_FOR_MANUAL_E2E,
                new WorkflowScope("LEGACY-" + taskType, "REPO_A", "legacy-ref"),
                "legacy-manual-" + taskType, "legacy-user", null, 5, now, now));

        mvc.perform(post("/api/v1/tasks/{id}/compatibility-complete", taskId)
                .header("X-Demo-User", "migration-operator")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/v1/tasks/{id}/audit", taskId).header("X-Demo-User", "migration-operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("LEGACY_STAGE_COMPLETED"))
                .andExpect(jsonPath("$[0].actorId").value("migration-operator"));
    }

    @Test
    void compatibilityCompletionCannotReplaceARealManualE2eResult() throws Exception {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        taskRepository.save(new WorkflowTask("TASK-REAL-MANUAL", TaskType.MANUAL_E2E,
                TaskStatus.WAITING_FOR_MANUAL_E2E,
                new WorkflowScope("REAL-MANUAL", "REPO_A", "real-ref"),
                "real-manual", "qa-user", null, 5, now, now));

        mvc.perform(post("/api/v1/tasks/TASK-REAL-MANUAL/compatibility-complete")
                .header("X-Demo-User", "migration-operator")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void simulatedPassUsesASimulatedActorAndCarriesNoQaExecutionEvidence() throws Exception {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        taskRepository.save(new WorkflowTask("TASK-SIMULATED-MANUAL", TaskType.MANUAL_E2E,
                TaskStatus.WAITING_FOR_CI,
                EvidenceClassification.SIMULATED_PASS,
                new WorkflowScope("SIMULATED-MANUAL", "REPO_A", "simulated-ref"),
                "simulated-manual", "SIMULATED-M7-RUNNER", null, 4, now, now));

        mvc.perform(post("/api/v1/tasks/TASK-SIMULATED-MANUAL/ci")
                .header("X-Demo-User", "SIMULATED-M7-RUNNER")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"expectedVersion":4,"state":"SIMULATED_PASS","buildFingerprint":"m7-simulated-build"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_MANUAL_E2E"))
                .andExpect(jsonPath("$.evidenceClassification").value("SIMULATED_PASS"));

        mvc.perform(post("/api/v1/tasks/TASK-SIMULATED-MANUAL/manual-e2e")
                .header("X-Demo-User", "SIMULATED-M7-RUNNER")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"expectedVersion":5,"result":"SIMULATED_PASS","actorRole":"SIMULATED_RUNNER"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.evidenceClassification").value("SIMULATED_PASS"));

        mvc.perform(get("/api/v1/tasks/TASK-SIMULATED-MANUAL/audit")
                .header("X-Demo-User", "SIMULATED-M7-RUNNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actorId").value("SIMULATED-M7-RUNNER"))
                .andExpect(jsonPath("$[1].actorId").value("SIMULATED-M7-RUNNER"))
                .andExpect(jsonPath("$[0].evidenceClassification").value("SIMULATED_PASS"))
                .andExpect(jsonPath("$[1].evidenceClassification").value("SIMULATED_PASS"));
    }

    @Test
    void rejectsSimulatedPassThatClaimsQaEvidence() throws Exception {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        taskRepository.save(new WorkflowTask("TASK-SIMULATED-CLAIM", TaskType.MANUAL_E2E,
                TaskStatus.WAITING_FOR_MANUAL_E2E,
                EvidenceClassification.SIMULATED_PASS,
                new WorkflowScope("SIMULATED-CLAIM", "REPO_A", "simulated-claim-ref"),
                "simulated-claim", "SIMULATED-M7-RUNNER", null, 5, now, now));

        mvc.perform(post("/api/v1/tasks/TASK-SIMULATED-CLAIM/manual-e2e")
                .header("X-Demo-User", "SIMULATED-M7-RUNNER")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"expectedVersion":5,"result":"SIMULATED_PASS","actorRole":"SIMULATED_RUNNER","actualResult":"QA passed","evidenceOrWaiver":"fake evidence"}
                    """))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "REAL,SIMULATED-M7-RUNNER,PASSED",
            "SIMULATED_PASS,developer-1,SIMULATED_PASS"
    })
    void rejectsActorClassificationMismatchForCiPass(
            EvidenceClassification classification, String actorId, String result) throws Exception {
        String taskId = "TASK-CI-ACTOR-" + classification;
        seedTask(taskId, TaskType.IMPLEMENTATION, TaskStatus.WAITING_FOR_CI, classification, 4);

        mvc.perform(post("/api/v1/tasks/{id}/ci", taskId)
                        .header("X-Demo-User", actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":4,\"state\":\"%s\",\"buildFingerprint\":\"actor-check\"}"
                                .formatted(result)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "REAL,SIMULATED-M7-RUNNER,PASS,QA",
            "SIMULATED_PASS,developer-1,SIMULATED_PASS,SIMULATED_RUNNER"
    })
    void rejectsActorClassificationMismatchForManualPass(
            EvidenceClassification classification, String actorId, String result, String actorRole) throws Exception {
        String taskId = "TASK-MANUAL-ACTOR-" + classification;
        seedTask(taskId, TaskType.MANUAL_E2E, TaskStatus.WAITING_FOR_MANUAL_E2E, classification, 5);
        String evidence = result.equals("PASS")
                ? ",\"caseId\":\"E2E-ACTOR\",\"executedAt\":\"2026-08-16T08:00:00Z\","
                        + "\"buildFingerprint\":\"actor-check\",\"actualResult\":\"passed\","
                        + "\"evidenceOrWaiver\":\"EVIDENCE-ACTOR\""
                : "";

        mvc.perform(post("/api/v1/tasks/{id}/manual-e2e", taskId)
                        .header("X-Demo-User", actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5,\"result\":\"%s\",\"actorRole\":\"%s\"%s}"
                                .formatted(result, actorRole, evidence)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "REAL,SIMULATED-M7-RUNNER",
            "SIMULATED_PASS,reviewer-1"
    })
    void rejectsActorClassificationMismatchForApproval(
            EvidenceClassification classification, String actorId) throws Exception {
        String suffix = classification.name();
        String taskId = "TASK-APPROVAL-ACTOR-" + suffix;
        String artifactId = "ART-APPROVAL-ACTOR-" + suffix;
        seedTask(taskId, TaskType.DESIGN, TaskStatus.WAITING_FOR_APPROVAL, classification, 3);
        artifacts.create(artifactId, taskId, ArtifactType.DESIGN_REPORT,
                java.util.List.of(new ArtifactSection("summary", "Summary", "Actor boundary")),
                "seed-user", null);

        mvc.perform(post("/api/v1/approvals")
                        .header("X-Demo-User", actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"%s\",\"artifactId\":\"%s\","
                                .formatted(taskId, artifactId)
                                + "\"artifactVersion\":1,\"expectedTaskVersion\":3}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "REAL,SIMULATED-M7-RUNNER",
            "SIMULATED_PASS,migration-operator"
    })
    void rejectsActorClassificationMismatchForCompatibilityCompletion(
            EvidenceClassification classification, String actorId) throws Exception {
        String taskId = "TASK-COMPAT-ACTOR-" + classification;
        seedTask(taskId, TaskType.DESIGN, TaskStatus.WAITING_FOR_MANUAL_E2E, classification, 5);

        mvc.perform(post("/api/v1/tasks/{id}/compatibility-complete", taskId)
                        .header("X-Demo-User", actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5}"))
                .andExpect(status().isBadRequest());
    }

    private void seedTask(String taskId, TaskType type, TaskStatus status,
            EvidenceClassification classification, long version) {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        taskRepository.save(new WorkflowTask(taskId, type, status, classification,
                new WorkflowScope(taskId, "REPO_A", "actor-check-ref"),
                "actor-check-" + taskId, "seed-user", null, version, now, now));
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }
}
