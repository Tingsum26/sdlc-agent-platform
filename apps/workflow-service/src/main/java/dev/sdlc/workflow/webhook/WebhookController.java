package dev.sdlc.workflow.webhook;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private final WebhookSignatureVerifier verifier;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;

    public WebhookController(
            WebhookSignatureVerifier verifier,
            WebhookDeliveryRepository deliveries,
            Clock clock) {
        this.verifier = verifier;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @PostMapping("/scm")
    ResponseEntity<Map<String, Object>> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader("X-GitHub-Event") String eventType) {
        verifier.verify(rawBody, signature);
        if (deliveries.exists(deliveryId)) {
            return ResponseEntity.ok(Map.of("duplicate", true, "deliveryId", deliveryId));
        }

        deliveries.save(new WebhookDelivery(deliveryId, eventType, Instant.now(clock)));
        return ResponseEntity.accepted().body(Map.of("duplicate", false, "deliveryId", deliveryId));
    }
}
