package dev.sdlc.workflow.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeterministicFakeTransportTest {

    @Test
    void returnsScriptedResponseAndCapturesSafeRequestMetadata() {
        DeterministicFakeTransport transport = new DeterministicFakeTransport();
        transport.script(EnterpriseProvider.JIRA, "ticket-read",
                new EnterpriseResponse(200, Map.of(), "{\"ticketId\":\"DEMO-123\"}", null, Instant.EPOCH));
        EnterpriseRequest request = new EnterpriseRequest(EnterpriseProvider.JIRA, "ticket-read", "GET",
                "/tickets/DEMO-123", Map.of("Accept", "application/json"), "", "corr-1", null, null);

        assertEquals(200, transport.execute(request, Duration.ofSeconds(2), EnterpriseCancellation.NEVER).statusCode());
        assertEquals("/tickets/DEMO-123", transport.ledger().get(0).relativePath());
    }

    @Test
    void classifiesRateLimitAuthenticationAndCancellationSafely() {
        EnterpriseAdapterException rateLimit = EnterpriseAdapterException.fromStatus(429);
        EnterpriseAdapterException authentication = EnterpriseAdapterException.fromStatus(401);
        assertEquals(EnterpriseErrorCategory.RATE_LIMIT, rateLimit.category());
        assertTrue(rateLimit.retryable());
        assertEquals(EnterpriseErrorCategory.AUTHENTICATION, authentication.category());
        assertFalse(authentication.retryable());

        DeterministicFakeTransport transport = new DeterministicFakeTransport();
        assertThrows(EnterpriseAdapterException.class, () -> transport.execute(
                new EnterpriseRequest(EnterpriseProvider.GHES, "checks", "GET", "/checks", Map.of(), "", "corr-2", null, null),
                Duration.ofSeconds(1), () -> true));
    }
}
