package dev.sdlc.workflow.api;

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
class JiraProjectionIT {

    private static final String USER = "PRINCIPAL-EMP-100";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectsAnUnknownTicket() throws Exception {
        mvc.perform(jiraDraft("UNKNOWN-TICKET", "ART-MISSING", 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnknownArtifact() throws Exception {
        createTicket("JIRA-UNKNOWN-ART");

        mvc.perform(jiraDraft("JIRA-UNKNOWN-ART", "ART-MISSING", 1))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnArtifactCreatedForAnotherTicketsTask() throws Exception {
        createTicket("JIRA-TICKET-A");
        createApprovedArtifact("JIRA-TICKET-B", "ART-OTHER-TICKET", "Other ticket");

        mvc.perform(jiraDraft("JIRA-TICKET-A", "ART-OTHER-TICKET", 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsAServerGeneratedProjectionWithoutCallerTextOrArtifactBody() throws Exception {
        createApprovedArtifact("JIRA-SAFE", "ART-SAFE", "Approved requirement scope");

        mvc.perform(post("/api/v1/jira-drafts")
                        .header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ticketId":"JIRA-SAFE","milestoneId":"REQ-APPROVED","artifactId":"ART-SAFE","artifactVersion":1}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("JIRA_ARTIFACT_SYNC_PENDING"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("JIRA-SAFE")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("REQUIREMENT_REPORT")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("APPROVED")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Approved requirement scope")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ARTIFACT_BODY_MUST_NOT_APPEAR"))));
    }

    @Test
    void redactsCompoundSecretKeysFromAllowlistedArtifactTitles() throws Exception {
        createApprovedArtifact("JIRA-COMPOUND-SECRET", "ART-COMPOUND-SECRET",
                "Requirement client_secret=COMPOUND_SECRET_VALUE");

        mvc.perform(jiraDraft("JIRA-COMPOUND-SECRET", "ART-COMPOUND-SECRET", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("COMPOUND_SECRET_VALUE"))));
    }

    @Test
    void rejectsTheOldFreeTextOnlyApiShape() throws Exception {
        mvc.perform(post("/api/v1/jira-drafts")
                        .header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"JIRA-OLD\",\"milestoneId\":\"REQ-APPROVED\",\"summary\":\"free text\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsJenkinsCiAndAdvancesTheTicket() throws Exception {
        mvc.perform(post("/api/v1/epics").header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epicId\":\"EPIC-M3-1\",\"title\":\"Fictional M3 epic\",\"journeyId\":\"ACCOUNT_OPENING\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/epics/{id}/activate", "EPIC-M3-1").header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M3-1").header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketId\":\"M3-API-1\",\"channel\":\"API\"}"))
                .andExpect(status().isCreated());

        long version = 0;
        for (String next : new String[] {"IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN"}) {
            String body = mvc.perform(post("/api/v1/tickets/{id}/advance", "M3-API-1")
                            .header("X-Demo-User", USER).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + version + ",\"target\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            version = json.readTree(body).path("version").asLong();
        }

        mvc.perform(post("/api/v1/tickets/{id}/ci", "M3-API-1").header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryAlias\":\"REPO_A\",\"revision\":\"0123456789abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CI_PASSED"))
                .andExpect(jsonPath("$.state").value("PASSED"));
    }

    private void createApprovedArtifact(String ticketId, String artifactId, String title) throws Exception {
        createTicket(ticketId);
        JsonNode workflow = json.readTree(mvc.perform(post("/api/v1/workflows/from-ticket")
                        .header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"%s\",\"repositoryAlias\":\"REPO_A\",\"targetCommit\":\"0123456789abcdef\"}".formatted(ticketId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String taskId = workflow.path("taskId").asText();

        mvc.perform(post("/api/v1/tasks/{taskId}/claim", taskId).header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"leaseMinutes\":15}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks/{taskId}/results", taskId).header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"artifactId":"%s","type":"REQUIREMENT_REPORT","sections":[
                              {"key":"summary","title":"%s","body":"ARTIFACT_BODY_MUST_NOT_APPEAR email=not-allowed@example.invalid api_key=super-secret https://unsafe.example"}
                            ]}
                            """.formatted(artifactId, title)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks/{taskId}/confirm", taskId).header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/approvals").header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"%s\",\"artifactId\":\"%s\",\"artifactVersion\":1,\"expectedTaskVersion\":3}"
                                .formatted(taskId, artifactId)))
                .andExpect(status().isOk());
    }

    private void createTicket(String ticketId) throws Exception {
        String epicId = "EPIC-" + ticketId;
        mvc.perform(post("/api/v1/epics").header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epicId\":\"%s\",\"title\":\"Jira projection epic\",\"journeyId\":\"ACCOUNT_OPENING\"}".formatted(epicId)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/epics/{epicId}/activate", epicId).header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/epics/{epicId}/tickets", epicId).header("X-Demo-User", USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketId\":\"%s\",\"channel\":\"API\"}".formatted(ticketId)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jiraDraft(
            String ticketId, String artifactId, int artifactVersion) {
        return post("/api/v1/jira-drafts").header("X-Demo-User", USER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketId\":\"%s\",\"milestoneId\":\"REQ-APPROVED\",\"artifactId\":\"%s\",\"artifactVersion\":%d}"
                        .formatted(ticketId, artifactId, artifactVersion));
    }
}
