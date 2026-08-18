package dev.sdlc.workflow.skip;

import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class SkipService {

    private final WorkflowTaskService workflowTasks;
    private final SkipAttestationRepository attestations;
    private final Clock clock;

    public SkipService(WorkflowTaskService workflowTasks, SkipAttestationRepository attestations, Clock clock) {
        this.workflowTasks = workflowTasks;
        this.attestations = attestations;
        this.clock = clock;
    }

    public SkipResult skip(String taskId, long expectedVersion, String reason, String discussedWith,
            String actorId, String actorRole, String correlationId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (actorRole == null || actorRole.isBlank()) {
            throw new IllegalArgumentException("actorRole is required");
        }
        WorkflowTask task = workflowTasks.getTask(taskId);
        WorkflowTask skipped = workflowTasks.skipTask(taskId, expectedVersion, actorId, correlationId);
        SkipAttestation attestation = new SkipAttestation(UUID.randomUUID().toString(), taskId, task.type().name(),
                reason, discussedWith == null ? "" : discussedWith, actorId, actorRole, clock.instant(), correlationId);
        attestations.save(attestation);
        return new SkipResult(skipped, attestation);
    }

    public List<SkipAttestation> listByTask(String taskId) {
        return attestations.findByTaskId(taskId);
    }
}
