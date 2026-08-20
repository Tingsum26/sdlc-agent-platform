package dev.sdlc.workflow.epic;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.evidence.EvidenceClassification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EpicWorkflowService {

    private final EpicWorkflowRepository epics;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public EpicWorkflowService(EpicWorkflowRepository epics, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.audits = audits;
        this.clock = clock;
    }

    public synchronized EpicWorkflow create(String epicId, String title, String journeyId, String actorId, String correlationId) {
        requireText(epicId, "epicId");
        requireText(title, "title");
        requireText(journeyId, "journeyId");
        if (epics.findById(epicId).isPresent()) {
            throw new IllegalArgumentException("Epic already exists: " + epicId);
        }
        Instant now = clock.instant();
        EpicWorkflow epic = new EpicWorkflow(epicId, title, journeyId, EpicStatus.CREATED, 0, now, now);
        epics.save(epic);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "EPIC_CREATED",
                "title=" + title, classificationFor(actorId), actorId, now, correlationId));
        return epic;
    }

    public synchronized EpicWorkflow activate(String epicId, long expectedVersion, String actorId, String correlationId) {
        EpicWorkflow epic = requireVersion(epicId, expectedVersion);
        if (epic.status() != EpicStatus.CREATED) {
            throw new WorkflowConflictException("Epic is not CREATED");
        }
        EpicWorkflow activated = epic.transitionedTo(EpicStatus.ACTIVE, clock.instant());
        epics.save(activated);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "EPIC_ACTIVATED",
                null, classificationFor(actorId), actorId, clock.instant(), correlationId));
        return activated;
    }

    public EpicWorkflow get(String epicId) {
        return epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
    }

    public List<EpicWorkflow> list() {
        return epics.findAll();
    }

    private EpicWorkflow requireVersion(String epicId, long expectedVersion) {
        EpicWorkflow epic = get(epicId);
        if (epic.version() != expectedVersion) {
            throw new WorkflowConflictException("Expected epic version " + expectedVersion + " but was " + epic.version());
        }
        return epic;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static EvidenceClassification classificationFor(String actorId) {
        return actorId.startsWith("SIMULATED-")
                ? EvidenceClassification.SIMULATED_PASS : EvidenceClassification.REAL;
    }
}
