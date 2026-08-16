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
                TaskStatus.WAITING_FOR_CI, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_CI, TaskStatus.WAITING_FOR_MANUAL_E2E, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_FOR_MANUAL_E2E, TaskStatus.COMPLETED, TaskStatus.BLOCKED, TaskStatus.CANCELLED);
        allow(TaskStatus.BLOCKED, TaskStatus.WAITING_FOR_LOCAL_COPILOT, TaskStatus.CANCELLED);
    }

    public void requireAllowed(TaskStatus source, TaskStatus target) {
        if (!allowed.getOrDefault(source, Set.of()).contains(target)) {
            throw new IllegalTaskTransitionException("Transition is not allowed: " + source + " -> " + target);
        }
    }

    private void allow(TaskStatus source, TaskStatus first, TaskStatus... rest) {
        EnumSet<TaskStatus> targets = EnumSet.of(first, rest);
        allowed.put(source, targets);
    }
}
