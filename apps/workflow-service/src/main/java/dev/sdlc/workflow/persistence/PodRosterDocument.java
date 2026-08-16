package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.pod.PodMembership;
import dev.sdlc.workflow.pod.PodRoster;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("podRosters")
public record PodRosterDocument(@Id String journeyId, long revision, List<PodMembership> memberships, Instant updatedAt) {
    public PodRosterDocument {
        memberships = List.copyOf(memberships);
    }

    public static PodRosterDocument fromDomain(PodRoster roster) {
        return new PodRosterDocument(roster.journeyId(), roster.revision(), roster.memberships(), roster.updatedAt());
    }

    public PodRoster toDomain() {
        return new PodRoster(journeyId, revision, memberships, updatedAt);
    }
}
