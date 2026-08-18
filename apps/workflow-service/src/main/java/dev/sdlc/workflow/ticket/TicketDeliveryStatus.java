package dev.sdlc.workflow.ticket;

public enum TicketDeliveryStatus {
    PLANNED,
    IN_ANALYSIS,
    WAITING_FOR_APPROVAL,
    IN_DEVELOPMENT,
    PR_OPEN,
    CI_PASSED,
    MERGED,
    RELEASED,
    FLAG_ENABLED,
    E2E_VERIFIED,
    BLOCKED,
    CANCELLED
}
