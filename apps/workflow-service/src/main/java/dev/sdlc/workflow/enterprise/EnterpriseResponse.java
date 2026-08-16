package dev.sdlc.workflow.enterprise;

import java.time.Instant;
import java.util.Map;

public record EnterpriseResponse(int statusCode, Map<String, String> headers, String body, String nextCursor, Instant observedAt) {
    public EnterpriseResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
        if (body.length() > 262_144) throw new EnterpriseAdapterException(
                EnterpriseErrorCategory.CONTRACT_MISMATCH, false, "Provider response exceeds 256 KiB");
    }
}
