package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void draftsAndPublishesAJiraCommentForACompletedStage() throws Exception {
        String created = mvc.perform(post("/api/v1/jira-drafts")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"DEMO-123\",\"milestoneId\":\"REQ-APPROVED\",\"summary\":\"Requirement approved\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("JIRA_ARTIFACT_SYNC_PENDING"))
                .andReturn().getResponse().getContentAsString();
        String projectionId = json.readTree(created).path("projectionId").asText();

        mvc.perform(post("/api/v1/jira-drafts/{id}/publish", projectionId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.attempts").value(1));

        mvc.perform(get("/api/v1/jira-drafts/{id}", projectionId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestoneId").value("REQ-APPROVED"));
    }

    @Test
    void rejectsRepublishingAPublishedDraft() throws Exception {
        String created = mvc.perform(post("/api/v1/jira-drafts")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"DEMO-789\",\"milestoneId\":\"DESIGN-APPROVED\",\"summary\":\"Design approved\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectionId = json.readTree(created).path("projectionId").asText();

        mvc.perform(post("/api/v1/jira-drafts/{id}/publish", projectionId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(post("/api/v1/jira-drafts/{id}/publish", projectionId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsPublishingAnUnknownDraft() throws Exception {
        mvc.perform(post("/api/v1/jira-drafts/{id}/publish", "JIRA-PROJ-NOPE")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsJenkinsCiAndAdvancesTheTicket() throws Exception {
        mvc.perform(post("/api/v1/epics")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epicId\":\"EPIC-M3-1\",\"title\":\"Fictional M3 epic\",\"journeyId\":\"ACCOUNT_OPENING\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/epics/{id}/activate", "EPIC-M3-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M3-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"M3-API-1\",\"channel\":\"API\"}"))
                .andExpect(status().isCreated());

        long version = 0;
        for (String next : new String[] {"IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN"}) {
            String body = mvc.perform(post("/api/v1/tickets/{id}/advance", "M3-API-1")
                            .header("X-Demo-User", "PRINCIPAL-EMP-100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + version + ",\"target\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            version = json.readTree(body).path("version").asLong();
        }

        mvc.perform(post("/api/v1/tickets/{id}/ci", "M3-API-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryAlias\":\"REPO_A\",\"revision\":\"0123456789abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CI_PASSED"))
                .andExpect(jsonPath("$.state").value("PASSED"));
    }
}
