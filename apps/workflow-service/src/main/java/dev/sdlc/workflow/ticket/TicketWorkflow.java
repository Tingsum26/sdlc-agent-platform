package dev.sdlc.workflow.ticket;

import dev.sdlc.workflow.epic.Channel;
import java.time.Instant;
import java.util.Objects;

public record TicketWorkflow(
        String ticketId,
        String epicId,
        Channel channel,
        TicketDeliveryStatus status,
        boolean pendingChangeConfirmation,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public TicketWorkflow {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public TicketWorkflow transitionedTo(TicketDeliveryStatus target, Instant now) {
        return new TicketWorkflow(ticketId, epicId, channel, target, pendingChangeConfirmation,
                version + 1, createdAt, now);
    }

    TicketWorkflow withChangeFlag(boolean flag, Instant now) {
        return new TicketWorkflow(ticketId, epicId, channel, status, flag, version + 1, createdAt, now);
    }
}
