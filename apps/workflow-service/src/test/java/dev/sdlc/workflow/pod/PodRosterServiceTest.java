package dev.sdlc.workflow.pod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PodRosterServiceTest {

    private final InMemoryPodRosterRepository repository = new InMemoryPodRosterRepository();
    private final InMemoryAuditEventRepository audits = new InMemoryAuditEventRepository();
    private final PodRosterService service = new PodRosterService(repository, audits,
            Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void importsRosterAtomicallyAndAuditsAuthenticatedActor() {
        PodRoster roster = service.importRoster("ACCOUNT_OPENING_DEMO", 0,
                List.of(member("MEM-1", "EMP-100", "PRINCIPAL-100", "QA", true)),
                "PRINCIPAL-ADMIN", "corr-pod-1");

        assertEquals(1, roster.revision());
        assertEquals("PRINCIPAL-ADMIN", audits.findByTaskId("POD:ACCOUNT_OPENING_DEMO").get(0).actorId());
        assertEquals("corr-pod-1", audits.findByTaskId("POD:ACCOUNT_OPENING_DEMO").get(0).correlationId());
    }

    @Test
    void rejectsDuplicateActiveEmployeeWithoutPersistingAnyRow() {
        List<PodMembership> duplicate = List.of(
                member("MEM-1", "EMP-100", "PRINCIPAL-100", "QA", true),
                member("MEM-2", "EMP-100", "PRINCIPAL-200", "API_DEVELOPER", true));

        assertThrows(InvalidPodRosterException.class, () -> service.importRoster(
                "ACCOUNT_OPENING_DEMO", 0, duplicate, "PRINCIPAL-ADMIN", "corr-pod-2"));
        assertEquals(true, repository.find("ACCOUNT_OPENING_DEMO").isEmpty());
    }

    @Test
    void rejectsStaleRevision() {
        service.importRoster("ACCOUNT_OPENING_DEMO", 0,
                List.of(member("MEM-1", "EMP-100", "PRINCIPAL-100", "QA", true)),
                "PRINCIPAL-ADMIN", "corr-pod-3");

        assertThrows(StaleRosterRevisionException.class, () -> service.importRoster(
                "ACCOUNT_OPENING_DEMO", 0,
                List.of(member("MEM-2", "EMP-200", "PRINCIPAL-200", "QA", true)),
                "PRINCIPAL-ADMIN", "corr-pod-4"));
    }

    static PodMembership member(String membershipId, String employeeId, String principalId, String role, boolean active) {
        return new PodMembership(membershipId, employeeId, principalId, "Fictional Member", role,
                "ACCOUNT_OPENING_DEMO", active, LocalDate.parse("2026-01-01"), null, List.of());
    }
}
