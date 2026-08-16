package dev.sdlc.workflow.enterprise;

import java.util.Map;
import java.util.Objects;

public record EnterpriseRequest(
        EnterpriseProvider provider, String operation, String method, String relativePath,
        Map<String, String> headers, String body, String correlationId, String idempotencyKey, String cursor) {
    public EnterpriseRequest {
        Objects.requireNonNull(provider, "provider");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation is required");
        if (method == null || !method.matches("GET|POST|PUT|PATCH|DELETE")) throw new IllegalArgumentException("method is invalid");
        if (relativePath == null || !relativePath.startsWith("/") || relativePath.contains("..")) throw new IllegalArgumentException("relativePath is invalid");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
        if (body.length() > 65_536) throw new IllegalArgumentException("enterprise request body exceeds 64 KiB");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        if (headers.keySet().stream().anyMatch(name -> name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("Cookie"))) {
            throw new IllegalArgumentException("credentials must be supplied by EnterpriseCredentialProvider");
        }
    }
}
