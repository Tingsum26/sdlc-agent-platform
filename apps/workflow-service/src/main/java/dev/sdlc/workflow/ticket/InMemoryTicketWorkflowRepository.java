package dev.sdlc.workflow.ticket;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryTicketWorkflowRepository implements TicketWorkflowRepository {
    private final ConcurrentMap<String, TicketWorkflow> tickets = new ConcurrentHashMap<>();

    @Override
    public Optional<TicketWorkflow> findById(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }

    @Override
    public TicketWorkflow save(TicketWorkflow ticket) {
        tickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    @Override
    public List<TicketWorkflow> findByEpicId(String epicId) {
        return tickets.values().stream().filter(ticket -> ticket.epicId().equals(epicId)).toList();
    }
}
