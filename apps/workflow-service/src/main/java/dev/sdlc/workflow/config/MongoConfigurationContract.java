package dev.sdlc.workflow.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MongoConfigurationContract {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> validate(Path yamlPath, Path indexManifestPath) throws IOException {
        List<String> violations = new ArrayList<>();
        String yaml = Files.readString(yamlPath);
        String lowerYaml = yaml.toLowerCase(Locale.ROOT);

        if (!yaml.contains("${WORKFLOW_MONGODB_URI}")) {
            violations.add("Mongo URI must use the WORKFLOW_MONGODB_URI environment placeholder");
        }
        if (!yaml.contains("${WORKFLOW_MONGODB_DATABASE}")) {
            violations.add("Mongo database must use the WORKFLOW_MONGODB_DATABASE environment placeholder");
        }
        if (lowerYaml.contains("localhost")
                || lowerYaml.contains("127.0.0.1")
                || lowerYaml.contains("host.docker.internal")) {
            violations.add("Local MongoDB addresses are forbidden");
        }

        JsonNode manifest = objectMapper.readTree(indexManifestPath.toFile());
        if (!"1.0".equals(manifest.path("schemaVersion").asText())) {
            violations.add("Mongo index manifest schemaVersion must be 1.0");
        }
        JsonNode collections = manifest.path("collections");
        if (!collections.isArray() || collections.isEmpty()) {
            violations.add("Mongo index manifest must declare at least one collection");
        } else {
            for (JsonNode collection : collections) {
                if (collection.path("name").asText().isBlank()) {
                    violations.add("Every Mongo collection requires a name");
                }
                JsonNode indexes = collection.path("indexes");
                if (!indexes.isArray() || indexes.isEmpty()) {
                    violations.add("Every Mongo collection requires at least one index");
                }
            }
        }
        return List.copyOf(violations);
    }
}
