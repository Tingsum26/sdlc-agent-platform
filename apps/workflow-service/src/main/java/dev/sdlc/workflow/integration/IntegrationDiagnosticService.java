package dev.sdlc.workflow.integration;

import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.evidence.EvidenceStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class IntegrationDiagnosticService {
    private final Clock clock;
    private final boolean simulated;

    public IntegrationDiagnosticService(Clock clock, boolean simulated) {
        this.clock = clock;
        this.simulated = simulated;
    }

    public List<IntegrationDiagnostic> diagnostics() {
        Instant observedAt = clock.instant();
        EvidenceStatus status = simulated
                ? EvidenceStatus.SIMULATED_PASS
                : EvidenceStatus.INTERNAL_VALIDATION_REQUIRED;
        String source = simulated ? "deterministic-fake" : "internal-http";
        String detail = simulated
                ? "Fictional deterministic response; no enterprise system was contacted."
                : "Run the connectivity check from the company network before relying on this integration.";
        return Arrays.stream(EnterpriseProvider.values())
                .map(provider -> new IntegrationDiagnostic(provider, status, observedAt, source, detail))
                .toList();
    }
}
