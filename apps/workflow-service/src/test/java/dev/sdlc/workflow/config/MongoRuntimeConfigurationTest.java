package dev.sdlc.workflow.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import dev.sdlc.workflow.persistence.MongoAuditEventRepository;
import dev.sdlc.workflow.persistence.MongoPodRosterRepository;
import dev.sdlc.workflow.persistence.MongoTaskAssignmentRepository;
import dev.sdlc.workflow.persistence.MongoWebhookDeliveryRepository;
import dev.sdlc.workflow.persistence.MongoWorkflowTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoOperations;

class MongoRuntimeConfigurationTest {

    @Test
    void activatesOnlyForMongoProfileAndWiresProductionShapedPorts() {
        MongoRuntimeConfiguration configuration = new MongoRuntimeConfiguration();
        MongoOperations mongo = mock(MongoOperations.class);

        assertArrayEquals(new String[] { "mongo" }, MongoRuntimeConfiguration.class.getAnnotation(Profile.class).value());
        assertInstanceOf(MongoWorkflowTaskRepository.class, configuration.workflowTaskRepository(mongo));
        assertInstanceOf(MongoAuditEventRepository.class, configuration.auditEventRepository(mongo));
        assertInstanceOf(MongoWebhookDeliveryRepository.class, configuration.webhookDeliveryRepository(mongo));
        assertInstanceOf(MongoPodRosterRepository.class, configuration.podRosterRepository(mongo));
        assertInstanceOf(MongoTaskAssignmentRepository.class, configuration.taskAssignmentRepository(mongo));
    }
}
