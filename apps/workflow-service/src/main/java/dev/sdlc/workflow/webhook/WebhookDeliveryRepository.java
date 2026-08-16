package dev.sdlc.workflow.webhook;

public interface WebhookDeliveryRepository {
    boolean exists(String deliveryId);

    void save(WebhookDelivery delivery);
}
