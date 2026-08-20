package dev.sdlc.workflow.task;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskTransitionPolicy {

    private final Map<TaskStatus, Set<TaskStatus>> allowed = new EnumMap<>(TaskStatus.class);

    public TaskTransitionPolicy() {
        allow(TaskStatus.CREATED, TaskStatus.WAITING_FOR_LOCAL_COPILOT, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_LOCAL_COPILOT, TaskStatus.LOCAL_COPILOT_RUNNING, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.LOCAL_COPILOT_RUNNING, TaskStatus.WAITING_FOR_LOCAL_COPILOT,
                TaskStatus.WAITING_FOR_USER_CONFIRMATION, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_USER_CONFIRMATION, TaskStatus.LOCAL_COPILOT_RUNNING,
                TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.LOCAL_COPILOT_RUNNING,
                TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_CI, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_MANUAL_E2E, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.BLOCKED, TaskStatus.WAITING_FOR_LOCAL_COPILOT, TaskStatus.CANCELLED);
    }

    public TaskStatus targetAfterApproval(TaskType type) {
        return switch (type) {
            case IMPLEMENTATION, TEST_GENERATION, PR_REVIEW, MANUAL_E2E -> TaskStatus.WAITING_FOR_CI;
            case REQUIREMENT_ANALYSIS, DESIGN, DELIVERY_COORDINATION, ONBOARDING_SYNC -> TaskStatus.COMPLETED;
        };
    }

    public TaskStatus targetAfterPassedCi(TaskType type) {
        return switch (type) {
            case IMPLEMENTATION, TEST_GENERATION, PR_REVIEW -> TaskStatus.COMPLETED;
            case MANUAL_E2E -> TaskStatus.WAITING_FOR_MANUAL_E2E;
            case REQUIREMENT_ANALYSIS, DESIGN, DELIVERY_COORDINATION, ONBOARDING_SYNC ->
                    throw new IllegalTaskTransitionException("Task type does not use the CI gate: " + type);
        };
    }

    public void requireAllowed(TaskType type, TaskStatus source, TaskStatus target) {
        if (allowed.getOrDefault(source, Set.of()).contains(target)) {
            return;
        }
        boolean stageTarget = source == TaskStatus.WAITING_FOR_APPROVAL && target == targetAfterApproval(type)
                || source == TaskStatus.WAITING_FOR_CI && target == targetAfterPassedCi(type)
                || source == TaskStatus.WAITING_FOR_MANUAL_E2E
                    && type == TaskType.MANUAL_E2E && target == TaskStatus.COMPLETED;
        if (!stageTarget) {
            throw new IllegalTaskTransitionException(
                    "Transition is not allowed for " + type + ": " + source + " -> " + target);
        }
    }

    private void allow(TaskStatus source, TaskStatus first, TaskStatus... rest) {
        EnumSet<TaskStatus> targets = EnumSet.of(first, rest);
        allowed.put(source, targets);
    }
}
