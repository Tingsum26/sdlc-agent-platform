package dev.sdlc.workflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MongoConfigurationContractTest {

    @Test
    void acceptsTheCompanyMongoEnvironmentContractAndIndexManifest() throws IOException {
        MongoConfigurationContract contract = new MongoConfigurationContract();

        assertThat(contract.validate(
                Path.of("src/main/resources/application-mongodb.example.yml"),
                Path.of("config/mongo-indexes.json"))).isEmpty();
    }

    @Test
    void rejectsLocalMongoAndLiteralCredentials(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("application.yml");
        Files.writeString(yaml, "spring:\n  data:\n    mongodb:\n      uri: mongodb://admin:secret@localhost:27017/workflow\n      database: workflow\n");
        Path indexes = tempDir.resolve("indexes.json");
        Files.writeString(indexes, "{\"schemaVersion\":\"1.0\",\"collections\":[]}");

        assertThat(new MongoConfigurationContract().validate(yaml, indexes))
                .contains("Mongo URI must use the WORKFLOW_MONGODB_URI environment placeholder")
                .contains("Local MongoDB addresses are forbidden");
    }
}
