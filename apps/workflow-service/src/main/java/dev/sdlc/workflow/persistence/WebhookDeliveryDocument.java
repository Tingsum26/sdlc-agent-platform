package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.webhook.WebhookDelivery;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("webhookDeliveries")
public record WebhookDeliveryDocument(@Id String deliveryId, String eventType, Instant receivedAt) {
    public static WebhookDeliveryDocument fromDomain(WebhookDelivery delivery) {
        return new WebhookDeliveryDocument(delivery.deliveryId(), delivery.eventType(), delivery.receivedAt());
    }

    public WebhookDelivery toDomain() {
        return new WebhookDelivery(deliveryId, eventType, receivedAt);
    }
}
