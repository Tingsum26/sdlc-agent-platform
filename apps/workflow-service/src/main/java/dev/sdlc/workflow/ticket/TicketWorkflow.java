package dev.sdlc.workflow.ticket;

import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Instant;
import java.util.Objects;

public record TicketWorkflow(
        String ticketId,
        String epicId,
        Channel channel,
        TicketDeliveryStatus status,
        EvidenceClassification evidenceClassification,
        boolean pendingChangeConfirmation,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public TicketWorkflow {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evidenceClassification, "evidenceClassification");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public TicketWorkflow(String ticketId, String epicId, Channel channel, TicketDeliveryStatus status,
            boolean pendingChangeConfirmation, long version, Instant createdAt, Instant updatedAt) {
        this(ticketId, epicId, channel, status, EvidenceClassification.REAL,
                pendingChangeConfirmation, version, createdAt, updatedAt);
    }

    public TicketWorkflow transitionedTo(TicketDeliveryStatus target, Instant now) {
        return new TicketWorkflow(ticketId, epicId, channel, target, evidenceClassification, pendingChangeConfirmation,
                version + 1, createdAt, now);
    }

    TicketWorkflow withChangeFlag(boolean flag, Instant now) {
        return new TicketWorkflow(ticketId, epicId, channel, status, evidenceClassification, flag, version + 1, createdAt, now);
    }
}
