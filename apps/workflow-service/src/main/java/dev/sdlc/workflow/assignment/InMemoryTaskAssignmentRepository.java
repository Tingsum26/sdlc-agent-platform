package dev.sdlc.workflow.assignment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTaskAssignmentRepository implements TaskAssignmentRepository {
    private final Map<String, TaskAssignment> assignments = new HashMap<>();

    @Override
    public synchronized Optional<TaskAssignment> find(String ticketId) {
        return Optional.ofNullable(assignments.get(ticketId));
    }

    @Override
    public synchronized TaskAssignment save(TaskAssignment assignment) {
        assignments.put(assignment.ticketId(), assignment);
        return assignment;
    }
}
