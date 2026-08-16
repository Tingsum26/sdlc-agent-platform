package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.assignment.AssignmentReason;
import dev.sdlc.workflow.assignment.TaskAssignment;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("taskAssignments")
public record TaskAssignmentDocument(
        @Id String ticketId, String journeyId, String requiredRole, String principalId,
        AssignmentReason reason, long version, Instant assignedAt) {
    public static TaskAssignmentDocument fromDomain(TaskAssignment assignment) {
        return new TaskAssignmentDocument(assignment.ticketId(), assignment.journeyId(), assignment.requiredRole(),
                assignment.principalId(), assignment.reason(), assignment.version(), assignment.assignedAt());
    }

    public TaskAssignment toDomain() {
        return new TaskAssignment(ticketId, journeyId, requiredRole, principalId, reason, version, assignedAt);
    }
}
