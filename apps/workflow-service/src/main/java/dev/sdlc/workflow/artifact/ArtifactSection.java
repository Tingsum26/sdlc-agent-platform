package dev.sdlc.workflow.artifact;

import java.util.Objects;

public record ArtifactSection(String key, String title, String body) {
    public ArtifactSection {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }
}
