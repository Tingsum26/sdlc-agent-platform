package dev.sdlc.workflow.epic;

import java.time.Instant;
import java.util.Objects;

public record EpicWorkflow(
        String epicId,
        String title,
        String journeyId,
        EpicStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public EpicWorkflow {
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    EpicWorkflow transitionedTo(EpicStatus target, Instant now) {
        return new EpicWorkflow(epicId, title, journeyId, target, version + 1, createdAt, now);
    }
}
