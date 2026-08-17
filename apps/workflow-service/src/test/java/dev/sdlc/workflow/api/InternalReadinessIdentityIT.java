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
class InternalReadinessIdentityIT {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void enrollsAndBindsANonGithubIdentity() throws Exception {
        String issued = mvc.perform(post("/api/v1/internal-readiness/identity/enrollment")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"EMP-777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andReturn().getResponse().getContentAsString();
        String code = json.readTree(issued).path("code").asText();

        mvc.perform(post("/api/v1/internal-readiness/identity/bind")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"displayLabel\":\"Fictional BA\","
                                + "\"maskedEmail\":\"b***@example.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value("PRINCIPAL-EMP-777"))
                .andExpect(jsonPath("$.employeeId").value("EMP-777"));
    }

    @Test
    void importMarksRosterMembersNotOnboardedUntilBound() throws Exception {
        mvc.perform(post("/api/v1/internal-readiness/pods/import")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journeyId":"ACCOUNT_OPENING",
                                  "expectedRevision":0,
                                  "memberships":[{
                                    "membershipId":"MEM-DEV-1",
                                    "employeeId":"EMP-201",
                                    "principalId":"PRINCIPAL-EMP-201",
                                    "displayLabel":"Fictional Developer",
                                    "role":"DEVELOPER",
                                    "journeyId":"ACCOUNT_OPENING",
                                    "active":true,
                                    "effectiveFrom":"2026-01-01",
                                    "aliases":[]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));

        mvc.perform(get("/api/v1/internal-readiness/pods/{journeyId}/members", "ACCOUNT_OPENING")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].principalId").value("PRINCIPAL-EMP-201"))
                .andExpect(jsonPath("$[0].onboardingStatus").value("NOT_ONBOARDED"));
    }
}
