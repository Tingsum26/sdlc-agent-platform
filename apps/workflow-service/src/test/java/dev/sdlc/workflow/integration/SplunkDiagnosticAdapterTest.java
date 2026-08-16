package dev.sdlc.workflow.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.DeterministicFakeTransport;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplunkDiagnosticAdapterTest {

    @Test
    void publishesOnlyAllowlistedRedactedBoundedFields() {
        DeterministicFakeTransport transport = new DeterministicFakeTransport();
        transport.script(EnterpriseProvider.SPLUNK, "diagnostic-batch",
                new EnterpriseResponse(202, Map.of(), "{}", null, Instant.EPOCH));
        SplunkDiagnosticAdapter adapter = new SplunkDiagnosticAdapter(transport, new ObjectMapper(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        adapter.publish(List.of(Map.of(
                "component", "workflow-service", "event", "adapter_test", "correlationId", "corr-1",
                "detail", "token=fictional-secret", "body", "must-not-pass")));

        String body = transport.ledger().get(0).body();
        assertTrue(body.contains("token=[redacted]"));
        assertFalse(body.contains("fictional-secret"));
        assertFalse(body.contains("must-not-pass"));
        assertThrows(IllegalArgumentException.class, () -> adapter.publish(List.of(Map.of(
                "component", "x".repeat(40_000), "event", "oversized"))));
    }
}
