package dev.sdlc.workflow.dependency;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class DependencyService {

    private final EpicWorkflowRepository epics;
    private final TicketWorkflowService tickets;
    private final DependencyRepository dependencies;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public DependencyService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.tickets = tickets;
        this.dependencies = dependencies;
        this.audits = audits;
        this.clock = clock;
    }

    public synchronized Dependency add(String epicId, String fromTicketId, String toTicketId, String actorId, String correlationId) {
        epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
        if (fromTicketId.equals(toTicketId)) {
            throw new IllegalArgumentException("A ticket cannot depend on itself");
        }
        if (!tickets.ticket(fromTicketId).epicId().equals(epicId) || !tickets.ticket(toTicketId).epicId().equals(epicId)) {
            throw new IllegalArgumentException("Both tickets must belong to the epic");
        }
        Dependency existing = dependencies.findByEpicId(epicId).stream()
                .filter(dep -> dep.fromTicketId().equals(fromTicketId)
                        && dep.toTicketId().equals(toTicketId)
                        && dep.status() == DependencyStatus.BLOCKING)
                .findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        String dependencyId = "DEP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Dependency dependency = new Dependency(dependencyId, epicId, fromTicketId, toTicketId,
                DependencyKind.REQUIRES_BEFORE, DependencyStatus.BLOCKING, 0, clock.instant());
        dependencies.save(dependency);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "DEPENDENCY_ADDED",
                fromTicketId + " -> " + toTicketId, actorId, clock.instant(), correlationId));
        return dependency;
    }

    public synchronized Dependency resolve(String dependencyId, long expectedVersion, String actorId, String correlationId) {
        Dependency dependency = requireVersion(dependencyId, expectedVersion);
        if (dependency.status() != DependencyStatus.BLOCKING) {
            throw new WorkflowConflictException("Dependency is not BLOCKING");
        }
        Dependency resolved = dependency.resolved(clock.instant());
        dependencies.save(resolved);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), dependency.epicId(), "EPIC",
                "DEPENDENCY_RESOLVED", dependencyId, actorId, clock.instant(), correlationId));
        return resolved;
    }

    public List<Dependency> listByEpic(String epicId) {
        return dependencies.findByEpicId(epicId);
    }

    private Dependency requireVersion(String dependencyId, long expectedVersion) {
        Dependency dependency = dependencies.findById(dependencyId)
                .orElseThrow(() -> new IllegalArgumentException("Dependency not found: " + dependencyId));
        if (dependency.version() != expectedVersion) {
            throw new WorkflowConflictException(
                    "Expected dependency version " + expectedVersion + " but was " + dependency.version());
        }
        return dependency;
    }
}
