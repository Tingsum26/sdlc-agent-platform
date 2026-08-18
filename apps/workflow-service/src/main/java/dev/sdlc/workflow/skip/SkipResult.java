package dev.sdlc.workflow.skip;

import dev.sdlc.workflow.task.WorkflowTask;

public record SkipResult(WorkflowTask task, SkipAttestation attestation) {
}
