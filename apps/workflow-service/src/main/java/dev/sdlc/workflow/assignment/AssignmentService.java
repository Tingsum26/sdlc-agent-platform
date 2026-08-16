package dev.sdlc.workflow.assignment;

import dev.sdlc.workflow.pod.PodMembership;
import dev.sdlc.workflow.pod.PodRosterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;

public final class AssignmentService {
    private final PodRosterRepository rosters;
    private final TaskAssignmentRepository assignments;
    private final Clock clock;

    public AssignmentService(PodRosterRepository rosters, TaskAssignmentRepository assignments, Clock clock) {
        this.rosters = rosters;
        this.assignments = assignments;
        this.clock = clock;
    }

    public TaskAssignment assign(String ticketId, String journeyId, String requiredRole, String explicitPrincipalId) {
        String principalId;
        AssignmentReason reason;
        if (explicitPrincipalId != null && !explicitPrincipalId.isBlank()) {
            principalId = explicitPrincipalId;
            reason = AssignmentReason.EXPLICIT_TICKET_ASSIGNEE;
        } else {
            principalId = rosters.find(journeyId).stream()
                    .flatMap(roster -> roster.memberships().stream())
                    .filter(member -> member.role().equals(requiredRole))
                    .filter(member -> member.activeOn(LocalDate.now(clock)))
                    .map(PodMembership::principalId)
                    .sorted(Comparator.naturalOrder())
                    .findFirst().orElse(TaskAssignment.UNASSIGNED);
            reason = TaskAssignment.UNASSIGNED.equals(principalId)
                    ? AssignmentReason.UNASSIGNED_QUEUE : AssignmentReason.POD_ROLE_MATCH;
        }
        long version = assignments.find(ticketId).map(TaskAssignment::version).orElse(0L) + 1;
        return assignments.save(new TaskAssignment(ticketId, journeyId, requiredRole, principalId, reason,
                version, clock.instant()));
    }
}
