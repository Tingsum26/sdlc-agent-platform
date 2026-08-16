package dev.sdlc.workflow.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.EnterpriseAdapterException;
import dev.sdlc.workflow.enterprise.EnterpriseCancellation;
import dev.sdlc.workflow.enterprise.EnterpriseErrorCategory;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseRequest;
import dev.sdlc.workflow.enterprise.EnterpriseResponse;
import dev.sdlc.workflow.enterprise.EnterpriseTransport;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class GhesEnterpriseAdapter {
    private final EnterpriseTransport transport;
    private final ObjectMapper mapper;
    private final Clock clock;

    public GhesEnterpriseAdapter(EnterpriseTransport transport, ObjectMapper mapper, Clock clock) {
        this.transport = transport; this.mapper = mapper; this.clock = clock;
    }

    public GhesCheckSummary checks(String repositoryAlias, String revision, String cursor) {
        EnterpriseResponse response = transport.execute(new EnterpriseRequest(EnterpriseProvider.GHES, "checks", "GET",
                "/repositories/" + repositoryAlias + "/commits/" + revision + "/checks", Map.of(), "",
                "ghes-" + clock.millis(), null, cursor), Duration.ofSeconds(10), EnterpriseCancellation.NEVER);
        try {
            JsonNode json = mapper.readTree(response.body());
            return new GhesCheckSummary(repositoryAlias, revision, CiState.valueOf(json.path("state").asText("UNKNOWN")), response.nextCursor());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false, "GHES response failed its contract");
        }
    }
}
