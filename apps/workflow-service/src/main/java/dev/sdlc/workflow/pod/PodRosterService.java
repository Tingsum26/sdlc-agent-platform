package dev.sdlc.workflow.pod;

import dev.sdlc.workflow.task.AuditEvent;
import dev.sdlc.workflow.task.AuditEventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PodRosterService {
    private final PodRosterRepository rosters;
    private final AuditEventRepository audits;
    private final Clock clock;

    public PodRosterService(PodRosterRepository rosters, AuditEventRepository audits, Clock clock) {
        this.rosters = rosters;
        this.audits = audits;
        this.clock = clock;
    }

    public PodRoster importRoster(String journeyId, long expectedRevision, List<PodMembership> rows,
            String actorId, String correlationId) {
        Objects.requireNonNull(rows, "rows");
        requireText(journeyId, "journeyId");
        requireText(actorId, "actorId");
        requireText(correlationId, "correlationId");
        validate(journeyId, rows, LocalDate.now(clock));
        PodRoster roster = new PodRoster(journeyId, expectedRevision + 1, rows, clock.instant());
        PodRoster saved = rosters.save(roster, expectedRevision);
        audits.append(new AuditEvent(UUID.randomUUID().toString(), "POD:" + journeyId,
                saved.revision(), actorId, "POD_ROSTER_IMPORTED", null, null,
                saved.revision(), clock.instant(), correlationId));
        return saved;
    }

    private static void validate(String journeyId, List<PodMembership> rows, LocalDate today) {
        Set<String> memberships = new HashSet<>();
        Set<String> activeEmployees = new HashSet<>();
        for (PodMembership row : rows) {
            if (!journeyId.equals(row.journeyId())) {
                throw new InvalidPodRosterException("Every membership must match the roster Journey");
            }
            if (!memberships.add(row.membershipId())) {
                throw new InvalidPodRosterException("Duplicate membership ID");
            }
            if (row.activeOn(today) && !activeEmployees.add(row.employeeId())) {
                throw new InvalidPodRosterException("Duplicate active employee ID");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
