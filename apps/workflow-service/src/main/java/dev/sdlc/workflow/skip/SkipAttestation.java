package dev.sdlc.workflow.skip;

import java.time.Instant;
import java.util.Objects;

public record SkipAttestation(
        String attestationId,
        String taskId,
        String stageType,
        String reason,
        String discussedWith,
        String actorId,
        String actorRole,
        Instant occurredAt,
        String correlationId) {

    public SkipAttestation {
        Objects.requireNonNull(attestationId, "attestationId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageType, "stageType");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorRole, "actorRole");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
