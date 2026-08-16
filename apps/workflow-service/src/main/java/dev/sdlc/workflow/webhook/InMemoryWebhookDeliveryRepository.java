package dev.sdlc.workflow.webhook;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWebhookDeliveryRepository implements WebhookDeliveryRepository {
    private final Set<String> deliveryIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean exists(String deliveryId) {
        return deliveryIds.contains(deliveryId);
    }

    @Override
    public void save(WebhookDelivery delivery) {
        deliveryIds.add(delivery.deliveryId());
    }
}
