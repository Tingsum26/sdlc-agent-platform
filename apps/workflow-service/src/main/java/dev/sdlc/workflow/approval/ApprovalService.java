package dev.sdlc.workflow.approval;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;

public final class ApprovalService {
    private final WorkflowTaskService tasks;
    private final ArtifactService artifacts;

    public ApprovalService(WorkflowTaskService tasks, ArtifactService artifacts) {
        this.tasks = tasks;
        this.artifacts = artifacts;
    }

    public synchronized ApprovalDecision approve(
            String taskId,
            String artifactId,
            int artifactVersion,
            long expectedTaskVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = tasks.getTask(taskId);
        ArtifactMetadata artifact = artifacts.requireArtifact(artifactId, artifactVersion);
        if (!artifact.taskId().equals(task.taskId())) {
            throw new IllegalArgumentException("Artifact does not belong to the workflow task");
        }
        ArtifactMetadata approved = artifacts.markApproved(artifactId, artifactVersion, actorId);
        WorkflowTask advanced = tasks.transition(taskId, TaskStatus.WAITING_FOR_APPROVAL,
                TaskStatus.WAITING_FOR_CI, expectedTaskVersion, actorId, correlationId);
        return new ApprovalDecision(actorId, "APPROVED", approved.approvedAt(), approved, advanced);
    }
}
