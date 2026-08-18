package dev.sdlc.workflow.change;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EpicChangeRequest(
        String changeRequestId,
        String epicId,
        String reason,
        ChangeUrgency urgency,
        String description,
        List<String> affectedTicketIds,
        List<String> approvedRoles,
        int requiredApprovals,
        ChangeRequestStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public EpicChangeRequest {
        Objects.requireNonNull(changeRequestId, "changeRequestId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        affectedTicketIds = List.copyOf(affectedTicketIds);
        approvedRoles = List.copyOf(approvedRoles);
    }

    public EpicChangeRequest withApproval(String role, Instant now) {
        List<String> roles = new ArrayList<>(approvedRoles);
        roles.add(role);
        ChangeRequestStatus next = roles.size() >= requiredApprovals
                ? ChangeRequestStatus.APPROVED : status;
        return new EpicChangeRequest(changeRequestId, epicId, reason, urgency, description, affectedTicketIds,
                List.copyOf(roles), requiredApprovals, next, version + 1, createdAt, now);
    }

    EpicChangeRequest rejectedNow(Instant now) {
        return new EpicChangeRequest(changeRequestId, epicId, reason, urgency, description, affectedTicketIds,
                approvedRoles, requiredApprovals, ChangeRequestStatus.REJECTED, version + 1, createdAt, now);
    }
}
