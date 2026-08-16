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
class InternalReadinessControllerIT {

    private static final String ACTOR = "PRINCIPAL-EMP-100";

    @Autowired
    private MockMvc mvc;

    @Test
    void exposesNonGithubIdentityImportsPodAndAssignsTicket() throws Exception {
        mvc.perform(get("/api/v1/internal-readiness/identity").header("X-Demo-User", ACTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("EMP-100"))
                .andExpect(jsonPath("$.source").value("ADMIN_BINDING"))
                .andExpect(jsonPath("$.githubLogin").doesNotExist());

        mvc.perform(post("/api/v1/internal-readiness/pods/import")
                        .header("X-Demo-User", ACTOR)
                        .header("X-Correlation-ID", "corr-api-pod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journeyId":"ACCOUNT_OPENING_DEMO",
                                  "expectedRevision":0,
                                  "memberships":[{
                                    "membershipId":"MEM-API-1",
                                    "employeeId":"EMP-100",
                                    "principalId":"PRINCIPAL-EMP-100",
                                    "displayLabel":"Fictional Scrum Master",
                                    "role":"SCRUM_MASTER",
                                    "journeyId":"ACCOUNT_OPENING_DEMO",
                                    "active":true,
                                    "effectiveFrom":"2026-01-01",
                                    "aliases":[]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));

        mvc.perform(post("/api/v1/internal-readiness/assignments")
                        .header("X-Demo-User", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"DEMO-123","journeyId":"ACCOUNT_OPENING_DEMO","requiredRole":"SCRUM_MASTER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(ACTOR))
                .andExpect(jsonPath("$.reason").value("POD_ROLE_MATCH"));

        mvc.perform(get("/api/v1/internal-readiness/assignments/DEMO-123").header("X-Demo-User", ACTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void importUsesAuthenticatedActorAndRejectsStaleRevision() throws Exception {
        mvc.perform(post("/api/v1/internal-readiness/pods/import")
                        .header("X-Demo-User", ACTOR)
                        .header("X-Correlation-ID", "corr-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"journeyId":"STALE_DEMO","expectedRevision":2,"memberships":[]}
                                """))
                .andExpect(status().isConflict());
    }
}
