package dev.sdlc.workflow.repotask;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RepoTaskService {

    private static final Map<RepoTaskStatus, Set<RepoTaskStatus>> ALLOWED = new EnumMap<>(RepoTaskStatus.class);

    static {
        allow(RepoTaskStatus.PLANNED, RepoTaskStatus.IN_PROGRESS, RepoTaskStatus.CANCELLED);
        allow(RepoTaskStatus.IN_PROGRESS, RepoTaskStatus.PR_OPEN, RepoTaskStatus.BLOCKED, RepoTaskStatus.PLANNED);
        allow(RepoTaskStatus.PR_OPEN, RepoTaskStatus.MERGED, RepoTaskStatus.BLOCKED);
        allow(RepoTaskStatus.BLOCKED, RepoTaskStatus.IN_PROGRESS, RepoTaskStatus.PLANNED);
    }

    private final TicketWorkflowService tickets;
    private final RepoTaskRepository repoTasks;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public RepoTaskService(TicketWorkflowService tickets, RepoTaskRepository repoTasks,
            DomainAuditEventRepository audits, Clock clock) {
        this.tickets = tickets;
        this.repoTasks = repoTasks;
        this.audits = audits;
        this.clock = clock;
    }

    public synchronized RepoTask create(String ticketId, String repositoryAlias, String baseCommit, String actorId,
            String correlationId) {
        var ticket = tickets.ticket(ticketId);
        String repoTaskId = "REPO-TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = clock.instant();
        RepoTask repoTask = new RepoTask(repoTaskId, ticketId, repositoryAlias, baseCommit,
                RepoTaskStatus.PLANNED, ticket.evidenceClassification(), 0, now, now);
        repoTasks.save(repoTask);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticketId, "TICKET", "REPO_TASK_CREATED",
                "repoTask=" + repoTaskId + " repo=" + repositoryAlias, repoTask.evidenceClassification(),
                actorId, now, correlationId));
        return repoTask;
    }

    public synchronized RepoTask transition(String repoTaskId, long expectedVersion, RepoTaskStatus target, String actorId,
            String correlationId) {
        RepoTask repoTask = requireVersion(repoTaskId, expectedVersion);
        if (!ALLOWED.getOrDefault(repoTask.status(), Set.of()).contains(target)) {
            throw new WorkflowConflictException("Repo task transition not allowed: " + repoTask.status() + " -> " + target);
        }
        RepoTask changed = repoTask.transitionedTo(target, clock.instant());
        repoTasks.save(changed);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), repoTask.ticketId(), "TICKET",
                "REPO_TASK_TRANSITIONED", "repoTask=" + repoTaskId + " " + repoTask.status() + "->" + target,
                repoTask.evidenceClassification(), actorId, clock.instant(), correlationId));
        return changed;
    }

    public List<RepoTask> listByTicket(String ticketId) {
        return repoTasks.findByTicketId(ticketId);
    }

    private RepoTask requireVersion(String repoTaskId, long expectedVersion) {
        RepoTask repoTask = repoTasks.findById(repoTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Repo task not found: " + repoTaskId));
        if (repoTask.version() != expectedVersion) {
            throw new WorkflowConflictException(
                    "Expected repo task version " + expectedVersion + " but was " + repoTask.version());
        }
        return repoTask;
    }

    private static void allow(RepoTaskStatus source, RepoTaskStatus first, RepoTaskStatus... rest) {
        ALLOWED.put(source, EnumSet.of(first, rest));
    }
}
