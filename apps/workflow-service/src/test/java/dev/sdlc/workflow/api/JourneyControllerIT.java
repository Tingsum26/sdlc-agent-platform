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
class JourneyControllerIT {
    @Autowired MockMvc mvc;

    private static final String MANIFEST = """
            {"schemaVersion":"1.0","journeyId":"ACCOUNT_OPENING","domainId":"CUSTOMER","version":1,
             "repositories":[
               {"alias":"API_REPO","role":"API","ref":"0123456789012345678901234567890123456789"},
               {"alias":"WEB_REPO","role":"WEB","ref":"0123456789012345678901234567890123456789"},
               {"alias":"IOS_REPO","role":"IOS","ref":"0123456789012345678901234567890123456789"},
               {"alias":"ANDROID_REPO","role":"ANDROID","ref":"0123456789012345678901234567890123456789"}],
             "screens":[{"screenId":"OPEN_ACCOUNT","client":"WEB","repositoryAlias":"WEB_REPO"}],
             "httpEdges":[{"edgeId":"EDGE_1","caller":"WEB_REPO","apiRepositoryAlias":"API_REPO","method":"POST","normalizedPath":"/accounts","requestSchemaRef":"schema/request","responseSchemaRef":"schema/response","commonHeaderRule":"X-Company-Context","authenticationClass":"OAUTH","compatibility":"ADDITIVE_WITH_FLAG","provenance":{"source":"CODE_SCAN","ref":"0123456789012345678901234567890123456789","evidenceId":"EVIDENCE_1"}}],
             "releasePolicy":{"webApiFirst":true,"nativeReleaseTrain":"MONTHLY_NATIVE","compatibilityWindowDays":60,"rollbackRule":"disable AWS toggle"},
             "featureFlag":{"required":true,"provider":"AWS_APP_CONFIG","ownerRole":"PRODUCT_OWNER"},
             "e2eOwners":[{"scenario":"HAPPY_PATH","ownerRole":"QA"}]}
            """;

    @Test
    void validatesAnalyzesAndRendersJourney() throws Exception {
        mvc.perform(post("/api/v1/journeys/validate").header("X-Demo-User", "PRINCIPAL-EMP-100").contentType(MediaType.APPLICATION_JSON).content(MANIFEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/v1/journeys/analyze").header("X-Demo-User", "PRINCIPAL-EMP-100").contentType(MediaType.APPLICATION_JSON).content(MANIFEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTRACT_PASS"));
        mvc.perform(post("/api/v1/journeys/report").header("X-Demo-User", "PRINCIPAL-EMP-100").contentType(MediaType.APPLICATION_JSON).content(MANIFEST))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML)).andExpect(content().string(org.hamcrest.Matchers.containsString("ACCOUNT_OPENING")));
    }
}
