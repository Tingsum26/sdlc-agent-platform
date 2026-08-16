package dev.sdlc.workflow.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepositoryContractFixtureTest {

    @Test
    void declaresRequiredCollectionsAndSafetyIndexes() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/mongo-indexes-v1.json")) {
            assertTrue(stream != null, "mongo-indexes-v1.json must be packaged");
            JsonNode root = new ObjectMapper().readTree(stream);
            assertEquals("1.0", root.path("schemaVersion").asText());
            Set<String> collections = new HashSet<>();
            root.path("collections").forEach(item -> {
                collections.add(item.path("name").asText());
                assertTrue(item.path("indexes").size() > 0, "each collection requires an index");
            });
            assertEquals(Set.of("workflowTasks", "auditEvents", "artifacts", "webhookDeliveries", "podRosters", "taskAssignments"), collections);
        }
    }
}
