package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.webhook.WebhookDelivery;
import dev.sdlc.workflow.webhook.WebhookDeliveryRepository;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoWebhookDeliveryRepository implements WebhookDeliveryRepository {
    static final String COLLECTION = "webhookDeliveries";
    private final MongoOperations mongo;

    public MongoWebhookDeliveryRepository(MongoOperations mongo) { this.mongo = mongo; }

    @Override
    public boolean exists(String deliveryId) {
        return mongo.exists(Query.query(Criteria.where("_id").is(deliveryId)), COLLECTION);
    }

    @Override
    public void save(WebhookDelivery delivery) {
        mongo.insert(WebhookDeliveryDocument.fromDomain(delivery), COLLECTION);
    }
}
