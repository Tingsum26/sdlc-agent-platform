package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class JourneyFreshnessIT {

    @Autowired
    private MockMvc mvc;

    private static final String COMMIT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String COMMIT_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void observesAndMarksStaleWithFreshnessReflected() throws Exception {
        mvc.perform(post("/api/v1/journeys/observations")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"ACCOUNT_OPENING\",\"repositoryAlias\":\"API_REPO\",\"commit\":\"" + COMMIT_A + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repositoryAlias").value("API_REPO"));

        mvc.perform(post("/api/v1/journeys/observations/stale")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"ACCOUNT_OPENING\",\"repositoryAlias\":\"WEB_REPO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staleMarked").value(true));

        String manifest = """
                {
                  "schemaVersion":"1.0",
                  "journeyId":"ACCOUNT_OPENING",
                  "domainId":"CUSTOMER",
                  "version":1,
                  "repositories":[
                    {"alias":"API_REPO","role":"API","ref":"%s"},
                    {"alias":"WEB_REPO","role":"WEB","ref":"%s"}
                  ],
                  "screens":[],"httpEdges":[],
                  "releasePolicy":{"webApiFirst":true,"nativeReleaseTrain":"MONTHLY_NATIVE","compatibilityWindowDays":60,"rollbackRule":"disable AWS toggle"},
                  "featureFlag":{"required":true,"provider":"AWS_APP_CONFIG","ownerRole":"PRODUCT_OWNER"},
                  "e2eOwners":[{"scenario":"HAPPY_PATH","ownerRole":"QA"}]
                }
                """.formatted(COMMIT_A, COMMIT_A);

        mvc.perform(post("/api/v1/journeys/freshness")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.API_REPO").value("LIVE"))
                .andExpect(jsonPath("$.WEB_REPO").value("STALE"));

        mvc.perform(post("/api/v1/journeys/report")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifest))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LIVE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STALE")));
    }
}
