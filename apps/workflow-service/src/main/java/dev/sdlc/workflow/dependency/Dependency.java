package dev.sdlc.workflow.dependency;

import java.time.Instant;
import java.util.Objects;

public record Dependency(
        String dependencyId,
        String epicId,
        String fromTicketId,
        String toTicketId,
        DependencyKind kind,
        DependencyStatus status,
        long version,
        Instant updatedAt) {

    public Dependency {
        Objects.requireNonNull(dependencyId, "dependencyId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(fromTicketId, "fromTicketId");
        Objects.requireNonNull(toTicketId, "toTicketId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Dependency resolved(Instant now) {
        return new Dependency(dependencyId, epicId, fromTicketId, toTicketId, kind, DependencyStatus.RESOLVED,
                version + 1, now);
    }
}
