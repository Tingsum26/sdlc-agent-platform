package dev.sdlc.workflow.integration;

public final class MockTicketAdapter implements TicketAdapter {
    @Override
    public TicketSnapshot getTicket(String ticketId) {
        return new TicketSnapshot(ticketId, "Public demo requirement",
                "A fictional high-level ticket used only by public fixtures.");
    }
}
