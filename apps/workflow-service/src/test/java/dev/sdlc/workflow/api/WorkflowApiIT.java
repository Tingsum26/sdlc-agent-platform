package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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
