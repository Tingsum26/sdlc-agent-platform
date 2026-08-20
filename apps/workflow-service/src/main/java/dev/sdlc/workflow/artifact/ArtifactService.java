package dev.sdlc.workflow.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class ArtifactService {

    private final ArtifactStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ArtifactService(ArtifactStore store, ObjectMapper objectMapper, Clock clock) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public synchronized ArtifactMetadata create(
            String artifactId,
            String taskId,
            ArtifactType type,
            List<ArtifactSection> sections,
            String actorId,
            String suppliedHash) {
        ArtifactMetadata latest = store.findLatest(artifactId).orElse(null);
        if (latest != null && latest.approved()) {
            throw new ArtifactImmutableException("Approved artifact versions cannot be replaced");
        }
        validateSections(sections);
        String hash = hash(sections);
        if (suppliedHash != null && !MessageDigest.isEqual(
                hash.getBytes(StandardCharsets.US_ASCII), suppliedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ArtifactHashMismatchException("Artifact content hash does not match canonical sections");
        }
        int version = latest == null ? 1 : latest.version() + 1;
        ArtifactMetadata artifact = new ArtifactMetadata(artifactId, taskId, type, version, hash,
                List.copyOf(sections), actorId, clock.instant(), null, null);
        return store.save(artifact);
    }

    public synchronized ArtifactMetadata markApproved(String artifactId, int version, String actorId) {
        ArtifactMetadata artifact = requireArtifact(artifactId, version);
        if (artifact.approved()) {
            return artifact;
        }
        return store.save(artifact.approvedBy(actorId, clock.instant()));
    }

    public synchronized void restore(ArtifactMetadata artifact) {
        store.save(artifact);
    }

    public synchronized void delete(ArtifactMetadata artifact) {
        store.delete(artifact.artifactId(), artifact.version());
    }

    public String renderHtml(String artifactId, int version) {
        ArtifactMetadata artifact = requireArtifact(artifactId, version);
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
                .append(escape(artifact.type().name()))
                .append("</title></head><body><main>");
        for (ArtifactSection section : artifact.sections()) {
            html.append("<section><h2>").append(escape(section.title())).append("</h2><pre>")
                    .append(escape(section.body())).append("</pre></section>");
        }
        return html.append("</main></body></html>").toString();
    }

    public ArtifactMetadata requireArtifact(String artifactId, int version) {
        return store.find(artifactId, version)
                .orElseThrow(() -> new ArtifactNotFoundException(artifactId, version));
    }

    private void validateSections(List<ArtifactSection> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("At least one artifact section is required");
        }
        for (ArtifactSection section : sections) {
            String body = section.body().toLowerCase(Locale.ROOT);
            if (body.contains("<script") || body.contains("javascript:")
                    || body.contains("<iframe") || body.contains("onerror=")) {
                throw new UnsafeArtifactContentException("Executable artifact content is forbidden");
            }
        }
    }

    private String hash(List<ArtifactSection> sections) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(sections);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash artifact", exception);
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
