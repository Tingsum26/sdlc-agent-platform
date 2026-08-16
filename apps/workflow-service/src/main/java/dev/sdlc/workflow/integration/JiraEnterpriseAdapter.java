package dev.sdlc.workflow.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.EnterpriseCancellation;
import dev.sdlc.workflow.enterprise.EnterpriseErrorCategory;
import dev.sdlc.workflow.enterprise.EnterpriseAdapterException;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseRequest;
import dev.sdlc.workflow.enterprise.EnterpriseResponse;
import dev.sdlc.workflow.enterprise.EnterpriseTransport;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class JiraEnterpriseAdapter implements TicketAdapter {
    private final EnterpriseTransport transport;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JiraEnterpriseAdapter(EnterpriseTransport transport, ObjectMapper mapper, Clock clock) {
        this.transport = transport;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public TicketSnapshot getTicket(String ticketId) {
        EnterpriseResponse response = execute("ticket-read", "GET", "/tickets/" + safeId(ticketId), "", null);
        JsonNode json = json(response.body());
        return new TicketSnapshot(required(json, "ticketId"), required(json, "summary"), required(json, "description"));
    }

    public void publishMilestone(String ticketId, String milestoneId, String summary) {
        try {
            String body = mapper.writeValueAsString(Map.of("milestoneId", milestoneId, "summary", summary));
            execute("milestone-comment", "POST", "/tickets/" + safeId(ticketId) + "/comments", body,
                    "jira:" + ticketId + ":" + milestoneId);
        } catch (JsonProcessingException exception) {
            throw contractMismatch();
        }
    }

    private EnterpriseResponse execute(String operation, String method, String path, String body, String idempotencyKey) {
        return transport.execute(new EnterpriseRequest(EnterpriseProvider.JIRA, operation, method, path,
                Map.of("Accept", "application/json"), body, "jira-" + clock.millis(), idempotencyKey, null),
                Duration.ofSeconds(10), EnterpriseCancellation.NEVER);
    }

    private JsonNode json(String body) {
        try { return mapper.readTree(body); } catch (JsonProcessingException exception) { throw contractMismatch(); }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw contractMismatch();
        return value;
    }

    private static String safeId(String id) {
        if (id == null || !id.matches("[A-Z0-9_-]{2,80}")) throw new IllegalArgumentException("provider ID is invalid");
        return id;
    }

    private static EnterpriseAdapterException contractMismatch() {
        return new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false, "Jira response failed its contract");
    }
}
