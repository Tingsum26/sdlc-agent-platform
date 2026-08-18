package dev.sdlc.workflow.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class EpicWorkflowIT {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void walksTheFullEpicScenarioWithChangeAndSkip() throws Exception {
        mvc.perform(post("/api/v1/epics")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"epicId":"EPIC-M2-1","title":"Fictional epic","journeyId":"ACCOUNT_OPENING"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        mvc.perform(post("/api/v1/epics/{id}/activate", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"M2-API-1\",\"channel\":\"API\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"M2-WEB-1\",\"channel\":\"WEB\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/epics/{id}/dependencies", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromTicketId\":\"M2-API-1\",\"toTicketId\":\"M2-WEB-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BLOCKING"));

        String web = mvc.perform(post("/api/v1/tickets/{id}/advance", "M2-WEB-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"target\":\"IN_ANALYSIS\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long version = json.readTree(web).path("version").asLong();
        for (String next : new String[] {"WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN", "CI_PASSED"}) {
            String body = mvc.perform(post("/api/v1/tickets/{id}/advance", "M2-WEB-1")
                            .header("X-Demo-User", "PRINCIPAL-EMP-100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + version + ",\"target\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            version = json.readTree(body).path("version").asLong();
        }

        mvc.perform(post("/api/v1/tickets/{id}/advance", "M2-WEB-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + version + ",\"target\":\"MERGED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mvc.perform(post("/api/v1/epics/{id}/change-requests", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Fictional scope change","urgency":"URGENT",
                                 "description":"Fictional detail","affectedTicketIds":["M2-API-1"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        String changeRequests = mvc.perform(get("/api/v1/epics/{id}/change-requests", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String changeRequestId = json.readTree(changeRequests).path(0).path("changeRequestId").asText();

        String firstApproval = mvc.perform(post("/api/v1/change-requests/{id}/approve", changeRequestId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"actorRole\":\"BUSINESS_OWNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long crVersion = json.readTree(firstApproval).path("version").asLong();

        mvc.perform(post("/api/v1/change-requests/{id}/approve", changeRequestId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + crVersion + ",\"actorRole\":\"TECHNICAL_OWNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post("/api/v1/change-requests/{id}/approve", changeRequestId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"actorRole\":\"BUSINESS_OWNER\"}"))
                .andExpect(status().isConflict());

        String tickets = mvc.perform(get("/api/v1/epics/{id}/tickets", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode api = findById(json.readTree(tickets), "M2-API-1");
        assertTrue(api != null, "M2-API-1 ticket should be listed for the epic");
        assertTrue(api.path("pendingChangeConfirmation").asBoolean(),
                "M2-API-1 should await change confirmation");

        mvc.perform(get("/api/v1/epics/{id}/resume", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epic.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tickets").isArray())
                .andExpect(jsonPath("$.tickets").isNotEmpty())
                .andExpect(jsonPath("$.auditTrail[?(@.action=='EPIC_CREATED')]").isNotEmpty());

        String skipped = mvc.perform(post("/api/v1/workflows/from-ticket")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"DEMO-456","repositoryAlias":"REPO_A","targetCommit":"0123456789abcdef0123456789abcdef01234567"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String skipTaskId = json.readTree(skipped).path("taskId").asText();
        long skipTaskVersion = json.readTree(skipped).path("version").asLong();

        mvc.perform(post("/api/v1/tasks/{taskId}/skip", skipTaskId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + skipTaskVersion + ",\"reason\":\"Fictional fast-track\","
                                + "\"discussedWith\":\"Fictional architect\",\"actorRole\":\"DEVELOPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.attestation.stageType").isNotEmpty());

        mvc.perform(get("/api/v1/tasks/{taskId}/skips", skipTaskId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("Fictional fast-track"));
    }

    @Test
    void requiresAnAuthenticatedDemoUser() throws Exception {
        mvc.perform(get("/api/v1/epics/EPIC-M2-1"))
                .andExpect(status().isUnauthorized());
    }

    private static JsonNode findById(JsonNode array, String ticketId) {
        for (JsonNode node : array) {
            if (ticketId.equals(node.path("ticketId").asText())) {
                return node;
            }
        }
        return null;
    }
}
