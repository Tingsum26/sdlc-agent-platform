package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.task.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
                    {"artifactId":"ART-LIFECYCLE","type":"REQUIREMENT_REPORT","sections":[{"key":"summary","title":"Summary","body":"Safe fictional evidence"}]}
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

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }
}
