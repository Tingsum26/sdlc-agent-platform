package dev.sdlc.workflow.integration;

import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.evidence.EvidenceStatus;
import java.time.Instant;

public record IntegrationDiagnostic(
        EnterpriseProvider provider,
        EvidenceStatus status,
        Instant observedAt,
        String source,
        String safeDetail) {
}
