package dev.sdlc.workflow.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.EnterpriseAdapterException;
import dev.sdlc.workflow.enterprise.EnterpriseCancellation;
import dev.sdlc.workflow.enterprise.EnterpriseErrorCategory;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseRequest;
import dev.sdlc.workflow.enterprise.EnterpriseTransport;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class ConfluenceEnterpriseAdapter {
    private final EnterpriseTransport transport;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConfluenceEnterpriseAdapter(EnterpriseTransport transport, ObjectMapper mapper, Clock clock) {
        this.transport = transport;
        this.mapper = mapper;
        this.clock = clock;
    }

    public ConfluencePage readPage(String pageId) {
        try {
            String body = transport.execute(new EnterpriseRequest(EnterpriseProvider.CONFLUENCE, "page-read", "GET",
                    "/pages/" + pageId, Map.of("Accept", "application/json"), "", "confluence-" + clock.millis(), null, null),
                    Duration.ofSeconds(10), EnterpriseCancellation.NEVER).body();
            JsonNode json = mapper.readTree(body);
            String returnedId = required(json, "pageId");
            return new ConfluencePage(returnedId, required(json, "title"), required(json, "body"),
                    "CONFLUENCE:" + returnedId + "@" + required(json, "sourceRevision"), false);
        } catch (JsonProcessingException exception) {
            throw new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false,
                    "Confluence response failed its contract");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false,
                "Confluence response failed its contract");
        return value;
    }
}
