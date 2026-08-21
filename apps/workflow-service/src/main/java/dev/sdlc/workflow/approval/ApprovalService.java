package dev.sdlc.workflow.approval;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.artifact.ArtifactService;
import dev.sdlc.workflow.artifact.TaskArtifactPolicy;
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
        synchronized (tasks) {
            WorkflowTask task = tasks.validateTransitionAfterApproval(taskId, expectedTaskVersion);
            ArtifactMetadata artifact = artifacts.requireArtifact(artifactId, artifactVersion);
            if (!artifact.taskId().equals(task.taskId())) {
                throw new IllegalArgumentException("Artifact does not belong to the workflow task");
            }
            TaskArtifactPolicy.requireCompatible(task.type(), artifact.type());
            ArtifactMetadata pending = artifacts.beginApproval(artifactId, artifactVersion, actorId);
            if (pending.approved()) {
                throw new IllegalStateException("Artifact was approved without a committed task transition");
            }
            WorkflowTask advanced;
            try {
                advanced = tasks.transitionAfterApproval(taskId, expectedTaskVersion, actorId, correlationId);
            } catch (RuntimeException exception) {
                try {
                    artifacts.restore(artifact);
                } catch (RuntimeException recoveryFailure) {
                    // The persisted PENDING state is deliberately retained. It
                    // is never publishable by Jira because approved() is false.
                    exception.addSuppressed(recoveryFailure);
                }
                throw exception;
            }
            try {
                ArtifactMetadata approved = artifacts.commitApproval(
                        artifactId, artifactVersion, actorId, advanced.version());
                return new ApprovalDecision(actorId, "APPROVED", approved.approvedAt(), approved, advanced);
            } catch (RuntimeException exception) {
                try {
                    tasks.compensateCommittedTransition(task, advanced);
                } catch (RuntimeException recoveryFailure) {
                    exception.addSuppressed(recoveryFailure);
                }
                try {
                    artifacts.restore(artifact);
                } catch (RuntimeException recoveryFailure) {
                    exception.addSuppressed(recoveryFailure);
                }
                throw exception;
            }
        }
    }
}
