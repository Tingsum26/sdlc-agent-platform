package dev.sdlc.workflow.ticket;

import java.util.List;
import java.util.Optional;

public interface TicketWorkflowRepository {
    Optional<TicketWorkflow> findById(String ticketId);
    TicketWorkflow save(TicketWorkflow ticket);
    List<TicketWorkflow> findByEpicId(String epicId);
}
