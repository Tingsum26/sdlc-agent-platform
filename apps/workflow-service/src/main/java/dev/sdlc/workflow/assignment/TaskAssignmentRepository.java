package dev.sdlc.workflow.assignment;

import java.util.Optional;

public interface TaskAssignmentRepository {
    Optional<TaskAssignment> find(String ticketId);

    TaskAssignment save(TaskAssignment assignment);
}
