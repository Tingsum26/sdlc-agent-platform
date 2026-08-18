package dev.sdlc.workflow.ticket;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.dependency.DependencyRepository;
import dev.sdlc.workflow.dependency.DependencyStatus;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicStatus;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TicketWorkflowService {

    private static final Map<TicketDeliveryStatus, Set<TicketDeliveryStatus>> ALLOWED =
            new EnumMap<>(TicketDeliveryStatus.class);

    static {
        allow(TicketDeliveryStatus.PLANNED, TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.IN_ANALYSIS, TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.WAITING_FOR_APPROVAL, TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.IN_DEVELOPMENT, TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.PR_OPEN, TicketDeliveryStatus.CI_PASSED,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.CI_PASSED, TicketDeliveryStatus.MERGED,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.MERGED, TicketDeliveryStatus.RELEASED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.RELEASED, TicketDeliveryStatus.FLAG_ENABLED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.FLAG_ENABLED, TicketDeliveryStatus.E2E_VERIFIED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.PLANNED,
                TicketDeliveryStatus.IN_DEVELOPMENT, TicketDeliveryStatus.CANCELLED);
    }

    private final EpicWorkflowRepository epics;
    private final TicketWorkflowRepository tickets;
    private final DependencyRepository dependencies;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public TicketWorkflowService(EpicWorkflowRepository epics, TicketWorkflowRepository tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.tickets = tickets;
        this.dependencies = dependencies;
        this.audits = audits;
        this.clock = clock;
    }

    public TicketWorkflow create(String epicId, String ticketId, Channel channel, String actorId, String correlationId) {
        requireText(ticketId, "ticketId");
        epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
        if (epics.findById(epicId).orElseThrow().status() != EpicStatus.ACTIVE) {
            throw new IllegalStateException("Epic must be ACTIVE to attach tickets");
        }
        if (tickets.findById(ticketId).isPresent()) {
            throw new IllegalArgumentException("Ticket already exists: " + ticketId);
        }
        Instant now = clock.instant();
        TicketWorkflow ticket = new TicketWorkflow(ticketId, epicId, channel,
                TicketDeliveryStatus.PLANNED, false, 0, now, now);
        tickets.save(ticket);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "TICKET_CREATED",
                "ticket=" + ticketId + " channel=" + channel, actorId, now, correlationId));
        return ticket;
    }

    public TicketWorkflow transition(String ticketId, long expectedVersion, TicketDeliveryStatus target,
            String actorId, String correlationId) {
        TicketWorkflow ticket = requireVersion(ticketId, expectedVersion);
        if (!ALLOWED.getOrDefault(ticket.status(), Set.of()).contains(target)) {
            throw new IllegalStateException("Transition not allowed: " + ticket.status() + " -> " + target);
        }
        if (target == TicketDeliveryStatus.MERGED && dependencies.findByEpicId(ticket.epicId()).stream()
                .anyMatch(dep -> dep.toTicketId().equals(ticketId) && dep.status() == DependencyStatus.BLOCKING)) {
            throw new IllegalStateException("Ticket is blocked by an unresolved dependency");
        }
        TicketWorkflow changed = ticket.transitionedTo(target, clock.instant());
        tickets.save(changed);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticket.epicId(), "EPIC",
                "TICKET_TRANSITIONED", "ticket=" + ticketId + " " + ticket.status() + "->" + target,
                actorId, clock.instant(), correlationId));
        return changed;
    }

    public TicketWorkflow markChangePending(String ticketId, String actorId, String correlationId) {
        TicketWorkflow ticket = ticket(ticketId);
        TicketWorkflow flagged = ticket.withChangeFlag(true, clock.instant());
        tickets.save(flagged);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticket.epicId(), "EPIC",
                "CHANGE_CONFIRMATION_REQUIRED", "ticket=" + ticketId, actorId, clock.instant(), correlationId));
        return flagged;
    }

    public TicketWorkflow ackChange(String ticketId, long expectedVersion, String actorId, String correlationId) {
        TicketWorkflow ticket = requireVersion(ticketId, expectedVersion);
        TicketWorkflow acked = ticket.withChangeFlag(false, clock.instant());
        tickets.save(acked);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticket.epicId(), "EPIC",
                "CHANGE_CONFIRMED", "ticket=" + ticketId, actorId, clock.instant(), correlationId));
        return acked;
    }

    public TicketWorkflow ticket(String ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
    }

    public List<TicketWorkflow> listByEpic(String epicId) {
        return tickets.findByEpicId(epicId);
    }

    private TicketWorkflow requireVersion(String ticketId, long expectedVersion) {
        TicketWorkflow ticket = ticket(ticketId);
        if (ticket.version() != expectedVersion) {
            throw new IllegalStateException("Expected ticket version " + expectedVersion + " but was " + ticket.version());
        }
        return ticket;
    }

    private static void allow(TicketDeliveryStatus source, TicketDeliveryStatus first, TicketDeliveryStatus... rest) {
        ALLOWED.put(source, EnumSet.of(first, rest));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
