package dev.sdlc.workflow.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.pod.InMemoryPodRosterRepository;
import dev.sdlc.workflow.pod.PodRosterService;
import dev.sdlc.workflow.pod.PodMembership;
import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssignmentServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryPodRosterRepository rosters = new InMemoryPodRosterRepository();
    private final InMemoryTaskAssignmentRepository assignments = new InMemoryTaskAssignmentRepository();
    private final AssignmentService service = new AssignmentService(rosters, assignments, clock);

    @BeforeEach
    void importRoster() {
        new PodRosterService(rosters, new InMemoryAuditEventRepository(), clock).importRoster(
                "ACCOUNT_OPENING_DEMO", 0,
                List.of(
                        member("MEM-2", "EMP-200", "PRINCIPAL-200", "QA", true),
                        member("MEM-1", "EMP-100", "PRINCIPAL-100", "QA", true),
                        member("MEM-3", "EMP-300", "PRINCIPAL-300", "QA", false)),
                "PRINCIPAL-ADMIN", "corr-setup");
    }

    @Test
    void explicitAssigneeTakesPrecedence() {
        assertEquals("PRINCIPAL-999", service.assign("DEMO-123", "ACCOUNT_OPENING_DEMO", "QA", "PRINCIPAL-999").principalId());
    }

    @Test
    void deterministicallyChoosesFirstActiveMatchingPrincipal() {
        assertEquals("PRINCIPAL-100", service.assign("DEMO-124", "ACCOUNT_OPENING_DEMO", "QA", null).principalId());
    }

    @Test
    void inactiveMemberIsExcludedAndMissingRoleGoesToQueue() {
        TaskAssignment assignment = service.assign("DEMO-125", "ACCOUNT_OPENING_DEMO", "ARCHITECT", null);
        assertEquals(TaskAssignment.UNASSIGNED, assignment.principalId());
        assertEquals(AssignmentReason.UNASSIGNED_QUEUE, assignment.reason());
    }

    private static PodMembership member(String membershipId, String employeeId, String principalId, String role, boolean active) {
        return new PodMembership(membershipId, employeeId, principalId, "Fictional Member", role,
                "ACCOUNT_OPENING_DEMO", active, LocalDate.parse("2026-01-01"), null, List.of());
    }
}
