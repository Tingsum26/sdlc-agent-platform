package dev.sdlc.workflow.artifact;

import dev.sdlc.workflow.task.TaskType;

public final class TaskArtifactPolicy {
    private TaskArtifactPolicy() { }

    public static ArtifactType expectedType(TaskType taskType) {
        return switch (taskType) {
            case REQUIREMENT_ANALYSIS -> ArtifactType.REQUIREMENT_REPORT;
            case DESIGN -> ArtifactType.DESIGN_REPORT;
            case IMPLEMENTATION, DELIVERY_COORDINATION, ONBOARDING_SYNC -> ArtifactType.DELIVERY_REPORT;
            case TEST_GENERATION -> ArtifactType.TEST_REPORT;
            case PR_REVIEW -> ArtifactType.PR_REVIEW_REPORT;
            case MANUAL_E2E -> ArtifactType.MANUAL_E2E_REPORT;
        };
    }

    public static void requireCompatible(TaskType taskType, ArtifactType artifactType) {
        ArtifactType expected = expectedType(taskType);
        if (artifactType != expected) {
            throw new IllegalArgumentException(
                    "Artifact type " + artifactType + " is not valid for task type " + taskType
                            + "; expected " + expected);
        }
    }
}
