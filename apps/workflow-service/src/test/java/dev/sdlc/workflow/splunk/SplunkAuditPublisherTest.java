package dev.sdlc.workflow.splunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.DeterministicFakeTransport;
import dev.sdlc.workflow.enterprise.EnterpriseAdapterException;
import dev.sdlc.workflow.enterprise.EnterpriseErrorCategory;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseRequest;
import dev.sdlc.workflow.enterprise.EnterpriseResponse;
import dev.sdlc.workflow.integration.SplunkDiagnosticAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplunkAuditPublisherTest {

    private record Fixture(SplunkAuditPublisher publisher, DeterministicFakeTransport transport) {
    }

    private Fixture fixture() {
        DeterministicFakeTransport transport = new DeterministicFakeTransport();
        transport.script(EnterpriseProvider.SPLUNK, "diagnostic-batch",
                new EnterpriseResponse(200, Map.of(), "{}", null, Instant.EPOCH));
        SplunkAuditPublisher publisher = new SplunkAuditPublisher(
                new SplunkDiagnosticAdapter(transport, new ObjectMapper(), Clock.systemUTC()));
        return new Fixture(publisher, transport);
    }

    @Test
    void emitsOnlyAllowlistedFieldsAndRedactsSecrets() {
        Fixture fixture = fixture();
        fixture.publisher().jiraProjection("DEMO-123", "REQ-APPROVED", "PUBLISHED",
                "corr-1", "password=secret-value should be dropped");

        assertEquals(1, fixture.transport().ledger().size());
        EnterpriseRequest request = fixture.transport().ledger().get(0);
        assertEquals(EnterpriseProvider.SPLUNK, request.provider());
        assertTrue(request.body().contains("\"event\""));
        assertTrue(!request.body().contains("secret-value"));
        assertTrue(request.body().contains("password=[redacted]"));
    }

    @Test
    void ciEventCarriesTicketAndState() {
        Fixture fixture = fixture();
        fixture.publisher().ciStatus("M3-API-1", "REPO_A", "PASSED", "corr-2");

        assertEquals(1, fixture.transport().ledger().size());
        EnterpriseRequest request = fixture.transport().ledger().get(0);
        assertTrue(request.body().contains("M3-API-1"));
        assertTrue(request.body().contains("PASSED"));
    }

    @Test
    void publishFailureIsSwallowedAsBestEffort() {
        DeterministicFakeTransport transport = new DeterministicFakeTransport();
        transport.scriptFailure(EnterpriseProvider.SPLUNK, "diagnostic-batch",
                new EnterpriseAdapterException(EnterpriseErrorCategory.TRANSPORT, true, "Fictional HEC outage"));
        SplunkAuditPublisher publisher = new SplunkAuditPublisher(
                new SplunkDiagnosticAdapter(transport, new ObjectMapper(), Clock.systemUTC()));

        publisher.ciStatus("M3-API-1", "REPO_A", "PASSED", "corr-3");
        publisher.jiraProjection("DEMO-123", "REQ-APPROVED", "PUBLISHED", "corr-4", "");
    }
}
