package dev.sdlc.workflow.webhook;

import java.time.Instant;

public record WebhookDelivery(String deliveryId, String eventType, Instant receivedAt) {
}
