package dev.sdlc.workflow.change;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ChangeRequestService {

    private static final Set<String> APPROVER_ROLES = Set.of("BUSINESS_OWNER", "TECHNICAL_OWNER");

    private final EpicWorkflowRepository epics;
    private final TicketWorkflowService tickets;
    private final ChangeRequestRepository requests;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public ChangeRequestService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            ChangeRequestRepository requests, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.tickets = tickets;
        this.requests = requests;
        this.audits = audits;
        this.clock = clock;
    }

    public synchronized EpicChangeRequest create(String epicId, String reason, ChangeUrgency urgency, String description,
            List<String> affectedTicketIds, String actorId, String correlationId) {
        epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
        String changeRequestId = "CR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = clock.instant();
        EpicChangeRequest request = new EpicChangeRequest(changeRequestId, epicId, reason, urgency, description,
                affectedTicketIds, List.of(), 2, ChangeRequestStatus.DRAFT, 0, now, now);
        requests.save(request);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "CHANGE_REQUEST_CREATED",
                "request=" + changeRequestId + " urgency=" + urgency, actorId, now, correlationId));
        return request;
    }

    public synchronized EpicChangeRequest approve(String changeRequestId, long expectedVersion, String actorId, String actorRole,
            String correlationId) {
        EpicChangeRequest request = requireVersion(changeRequestId, expectedVersion);
        if (request.status() != ChangeRequestStatus.DRAFT) {
            throw new WorkflowConflictException("Change request is not DRAFT");
        }
        if (!APPROVER_ROLES.contains(actorRole)) {
            throw new IllegalArgumentException("Approver role must be BUSINESS_OWNER or TECHNICAL_OWNER");
        }
        if (request.approvedRoles().contains(actorRole)) {
            throw new WorkflowConflictException("Role already approved this change request");
        }
        EpicChangeRequest updated = request.withApproval(actorRole, clock.instant());
        if (updated.status() == ChangeRequestStatus.APPROVED) {
            for (String ticketId : updated.affectedTicketIds()) {
                tickets.markChangePending(ticketId, actorId, correlationId);
            }
        }
        requests.save(updated);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), request.epicId(), "EPIC",
                updated.status() == ChangeRequestStatus.APPROVED ? "CHANGE_REQUEST_APPROVED" : "CHANGE_REQUEST_APPROVAL_ADDED",
                "request=" + changeRequestId + " role=" + actorRole, actorId, clock.instant(), correlationId));
        return updated;
    }

    public synchronized EpicChangeRequest reject(String changeRequestId, long expectedVersion, String actorId, String correlationId) {
        EpicChangeRequest request = requireVersion(changeRequestId, expectedVersion);
        if (request.status() != ChangeRequestStatus.DRAFT) {
            throw new WorkflowConflictException("Change request is not DRAFT");
        }
        EpicChangeRequest rejected = request.rejectedNow(clock.instant());
        requests.save(rejected);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), request.epicId(), "EPIC",
                "CHANGE_REQUEST_REJECTED", "request=" + changeRequestId, actorId, clock.instant(), correlationId));
        return rejected;
    }

    public List<EpicChangeRequest> listByEpic(String epicId) {
        return requests.findByEpicId(epicId);
    }

    private EpicChangeRequest requireVersion(String changeRequestId, long expectedVersion) {
        EpicChangeRequest request = requests.findById(changeRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Change request not found: " + changeRequestId));
        if (request.version() != expectedVersion) {
            throw new WorkflowConflictException(
                    "Expected change request version " + expectedVersion + " but was " + request.version());
        }
        return request;
    }
}
