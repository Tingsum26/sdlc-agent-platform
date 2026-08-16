package dev.sdlc.workflow.webhook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "workflow.webhook.secret=demo-webhook-secret")
@AutoConfigureMockMvc
@ActiveProfiles("fake")
class WebhookControllerIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void verifiesRawBytesAndDeduplicatesDeliveryIds() throws Exception {
        byte[] body = "{\"action\":\"opened\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        String signature = signature(body);

        mvc.perform(post("/api/v1/webhooks/scm")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Delivery", "delivery-001")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(false));

        mvc.perform(post("/api/v1/webhooks/scm")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Delivery", "delivery-001")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void rejectsAnInvalidSignatureWithoutEchoingTheBody() throws Exception {
        mvc.perform(post("/api/v1/webhooks/scm")
                        .header("X-Hub-Signature-256", "sha256=bad")
                        .header("X-GitHub-Delivery", "delivery-002")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"must-not-echo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Webhook signature validation failed"));
    }

    private String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("demo-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
    }
}
