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
import java.time.Instant;
import java.util.Map;

public final class JenkinsEnterpriseAdapter implements CiStatusAdapter {
    private static final Duration MAX_AGE = Duration.ofHours(12);
    private final EnterpriseTransport transport;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JenkinsEnterpriseAdapter(EnterpriseTransport transport, ObjectMapper mapper, Clock clock) {
        this.transport = transport; this.mapper = mapper; this.clock = clock;
    }

    @Override
    public CiStatus getStatus(String repositoryAlias, String revision) {
        String body = transport.execute(new EnterpriseRequest(EnterpriseProvider.JENKINS, "build-status", "GET",
                "/builds/" + repositoryAlias + "/" + revision, Map.of(), "", "jenkins-" + clock.millis(), null, null),
                Duration.ofSeconds(10), EnterpriseCancellation.NEVER).body();
        try {
            JsonNode json = mapper.readTree(body);
            Instant observedAt = Instant.parse(json.path("observedAt").asText());
            CiState state = Duration.between(observedAt, clock.instant()).compareTo(MAX_AGE) > 0
                    ? CiState.UNKNOWN : CiState.valueOf(json.path("state").asText("UNKNOWN"));
            return new CiStatus(json.path("repositoryAlias").asText(repositoryAlias),
                    json.path("revision").asText(revision), state, json.path("detailsUrl").asText());
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new EnterpriseAdapterException(EnterpriseErrorCategory.CONTRACT_MISMATCH, false,
                    "Jenkins response failed its contract");
        }
    }
}
