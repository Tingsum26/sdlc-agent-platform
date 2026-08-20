package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fake")
class WorkflowApiIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WorkflowTaskService tasks;

    @Test
    void createsTheRequestedWorkflowStageWhileDefaultingOldCallersToRequirementAnalysis() throws Exception {
        mvc.perform(post("/api/v1/workflows/from-ticket")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"TYPE-1\",\"repositoryAlias\":\"REPO_A\",\"targetCommit\":\"0123456789abcdef\",\"type\":\"DESIGN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DESIGN"));

        mvc.perform(post("/api/v1/workflows/from-ticket")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"TYPE-2\",\"repositoryAlias\":\"REPO_A\",\"targetCommit\":\"0123456789abcdef\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REQUIREMENT_ANALYSIS"));
    }

    @Test
    void retainsTheLegacyRequirementIdempotencyKeyForOmittedOrExplicitRequirementType() throws Exception {
        tasks.createTask("TASK-LEGACY-1", TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("LEGACY-1", "REPO_A", "0123456789abcdef"),
                "ticket:LEGACY-1:0123456789abcdef", "PRINCIPAL-EMP-100", "legacy-seed");

        for (String body : new String[] {
                "{\"ticketId\":\"LEGACY-1\",\"repositoryAlias\":\"REPO_A\",\"targetCommit\":\"0123456789abcdef\"}",
                "{\"ticketId\":\"LEGACY-1\",\"repositoryAlias\":\"REPO_A\",\"targetCommit\":\"0123456789abcdef\",\"type\":\"REQUIREMENT_ANALYSIS\"}" }) {
            mvc.perform(post("/api/v1/workflows/from-ticket").header("X-Demo-User", "PRINCIPAL-EMP-100")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.taskId").value("TASK-LEGACY-1"))
                    .andExpect(jsonPath("$.type").value("REQUIREMENT_ANALYSIS"));
        }
    }

    @Test
    void repositoryIdentitySeparatesSameTicketCommitAndTypeWhileLegacyLookupStaysInScope() throws Exception {
        tasks.createTask("TASK-LEGACY-SCOPED", TaskType.REQUIREMENT_ANALYSIS,
                new WorkflowScope("LEGACY-SCOPED", "REPO_A", "same-ref"),
                "ticket:LEGACY-SCOPED:same-ref", "PRINCIPAL-EMP-100", "legacy-seed");

        mvc.perform(post("/api/v1/workflows/from-ticket").header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"LEGACY-SCOPED\",\"repositoryAlias\":\"REPO_A\",\"targetCommit\":\"same-ref\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.taskId").value("TASK-LEGACY-SCOPED"));

        String repoB = mvc.perform(post("/api/v1/workflows/from-ticket").header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"LEGACY-SCOPED\",\"repositoryAlias\":\"REPO_B\",\"targetCommit\":\"same-ref\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.scope.repositoryAlias").value("REPO_B"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(repoB)
                .path("taskId").asText()).isNotEqualTo("TASK-LEGACY-SCOPED");
    }

    @Test
    void createsAndListsAWorkflowFromAFictionalTicket() throws Exception {
        String created = mvc.perform(post("/api/v1/workflows/from-ticket")
                        .header("X-Demo-User", "developer-1")
                        .header("X-Correlation-ID", "corr-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketId":"DEMO-123",
                                  "repositoryAlias":"REPO_A",
                                  "targetCommit":"0123456789abcdef0123456789abcdef01234567"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope.ticketId").value("DEMO-123"))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_LOCAL_COPILOT"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        String taskId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("taskId").asText();

        mvc.perform(get("/api/v1/tasks/{taskId}", taskId).header("X-Demo-User", "developer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

        mvc.perform(get("/api/v1/tasks").header("X-Demo-User", "developer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scope.repositoryAlias").value("REPO_A"));
    }

    @Test
    void returnsConflictForAStaleClaimVersion() throws Exception {
        String response = mvc.perform(post("/api/v1/workflows/from-ticket")
                        .header("X-Demo-User", "developer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketId":"DEMO-STALE",
                                  "repositoryAlias":"REPO_A",
                                  "targetCommit":"1123456789abcdef0123456789abcdef01234567"
                                }
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("taskId").asText();

        mvc.perform(post("/api/v1/tasks/{id}/claim", taskId)
                        .header("X-Demo-User", "developer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":9,\"leaseMinutes\":15}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void requiresAnAuthenticatedDemoUserForMutations() throws Exception {
        mvc.perform(post("/api/v1/workflows/from-ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketId":"DEMO-UNAUTHORIZED",
                                  "repositoryAlias":"REPO_A",
                                  "targetCommit":"2123456789abcdef0123456789abcdef01234567"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
