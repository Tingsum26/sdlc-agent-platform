package dev.sdlc.workflow.enterprise;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaHttpEnterpriseTransport implements EnterpriseTransport {
    private final EnterpriseAdapterProperties properties;
    private final EnterpriseCredentialProvider credentials;
    private final EnterpriseHttpExecutor executor;

    public JavaHttpEnterpriseTransport(
            EnterpriseAdapterProperties properties,
            EnterpriseCredentialProvider credentials,
            EnterpriseHttpExecutor executor) {
        this.properties = properties;
        this.credentials = credentials;
        this.executor = executor;
    }

    @Override
    public EnterpriseResponse execute(EnterpriseRequest request, Duration timeout, EnterpriseCancellation cancellation) {
        if (cancellation.cancelled()) throw new EnterpriseAdapterException(
                EnterpriseErrorCategory.CANCELLED, false, "Provider operation cancelled");
        URI uri = properties.baseUri(request.provider()).resolve(request.relativePath());
        URI base = properties.baseUri(request.provider());
        if (!base.getHost().equalsIgnoreCase(uri.getHost()) || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false, "Provider URI escaped its configured host");
        }
        Map<String, String> headers = new LinkedHashMap<>(request.headers());
        headers.put("X-Correlation-ID", request.correlationId());
        if (request.idempotencyKey() != null) headers.put("Idempotency-Key", request.idempotencyKey());
        credentials.authorizationValue(request.provider()).ifPresent(value -> headers.put("Authorization", value));
        EnterpriseResponse response = executor.execute(request.method(), uri, Map.copyOf(headers), request.body(), timeout);
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw EnterpriseAdapterException.fromStatus(response.statusCode());
        return response;
    }
}
