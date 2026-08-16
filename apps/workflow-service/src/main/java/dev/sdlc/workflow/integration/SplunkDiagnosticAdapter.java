package dev.sdlc.workflow.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.EnterpriseAdapterException;
import dev.sdlc.workflow.enterprise.EnterpriseCancellation;
import dev.sdlc.workflow.enterprise.EnterpriseErrorCategory;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseRequest;
import dev.sdlc.workflow.enterprise.EnterpriseTransport;
import dev.sdlc.workflow.logging.StructuredLogSanitizer;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SplunkDiagnosticAdapter {
    private static final Set<String> ALLOWED = Set.of(
            "component", "event", "correlationId", "taskId", "status", "durationMs", "detail");
    private static final int MAX_BATCH_BYTES = 32 * 1024;
    private final EnterpriseTransport transport;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SplunkDiagnosticAdapter(EnterpriseTransport transport, ObjectMapper mapper, Clock clock) {
        this.transport = transport; this.mapper = mapper; this.clock = clock;
    }

    public void publish(List<Map<String, Object>> events) {
        List<Map<String, Object>> safeEvents = new ArrayList<>();
        for (Map<String, Object> event : events) {
            Map<String, Object> safe = new LinkedHashMap<>();
            event.forEach((key, value) -> {
                if (ALLOWED.contains(key)) safe.put(key, value instanceof String text ? StructuredLogSanitizer.safe(text) : value);
            });
            safeEvents.add(Map.copyOf(safe));
        }
        try {
            String body = mapper.writeValueAsString(safeEvents);
            if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BATCH_BYTES) {
                throw new IllegalArgumentException("Splunk diagnostic batch exceeds 32 KiB");
            }
            transport.execute(new EnterpriseRequest(EnterpriseProvider.SPLUNK, "diagnostic-batch", "POST",
                    "/services/collector/event", Map.of("Content-Type", "application/json"), body,
                    "splunk-" + clock.millis(), "splunk:" + clock.millis(), null),
                    Duration.ofSeconds(5), EnterpriseCancellation.NEVER);
        } catch (JsonProcessingException exception) {
            throw new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false,
                    "Diagnostic batch failed serialization");
        }
    }
}
