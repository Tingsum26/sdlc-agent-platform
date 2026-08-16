package dev.sdlc.workflow.assignment;

import java.time.Instant;
import java.util.Objects;

public record TaskAssignment(
        String ticketId,
        String journeyId,
        String requiredRole,
        String principalId,
        AssignmentReason reason,
        long version,
        Instant assignedAt) {
    public static final String UNASSIGNED = "UNASSIGNED";

    public TaskAssignment {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(requiredRole, "requiredRole");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(assignedAt, "assignedAt");
    }
}
