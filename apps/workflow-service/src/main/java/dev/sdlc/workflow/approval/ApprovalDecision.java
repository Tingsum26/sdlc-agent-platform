package dev.sdlc.workflow.approval;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.task.WorkflowTask;
import java.time.Instant;

public record ApprovalDecision(
        String actorId,
        String decision,
        Instant decidedAt,
        ArtifactMetadata artifact,
        WorkflowTask task) {
}
