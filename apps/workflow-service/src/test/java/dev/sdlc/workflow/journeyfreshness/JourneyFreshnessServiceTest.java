package dev.sdlc.workflow.journeyfreshness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.journey.JourneyManifest;
import dev.sdlc.workflow.journey.JourneyRepositoryEntry;
import dev.sdlc.workflow.journey.RepositoryRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JourneyFreshnessServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final String COMMIT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String COMMIT_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final JourneyManifest MANIFEST = new JourneyManifest("1.0", "ACCOUNT_OPENING", "CUSTOMER", 1,
            List.of(
                    new JourneyRepositoryEntry("API_REPO", RepositoryRole.API, COMMIT_A),
                    new JourneyRepositoryEntry("WEB_REPO", RepositoryRole.WEB, COMMIT_A)),
            List.of(), List.of(), null, null, List.of());

    private record Fixture(JourneyFreshnessService service, InMemoryJourneyObservationRepository repository) {
    }

    private Fixture fixture() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryJourneyObservationRepository repository = new InMemoryJourneyObservationRepository();
        return new Fixture(new JourneyFreshnessService(repository, clock), repository);
    }

    @Test
    void unobservedRepositoriesAreOffline() {
        Fixture fixture = fixture();
        assertEquals(JourneyFreshness.OFFLINE, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
        assertEquals(JourneyFreshness.OFFLINE, fixture.service().freshnessFor(MANIFEST).get("WEB_REPO"));
    }

    @Test
    void matchingRecentObservationIsLive() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "API_REPO", COMMIT_A, NOW);
        assertEquals(JourneyFreshness.LIVE, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
    }

    @Test
    void mismatchedRecentObservationIsStale() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "API_REPO", COMMIT_B, NOW);
        assertEquals(JourneyFreshness.STALE, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
    }

    @Test
    void oldObservationIsDelayed() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "API_REPO", COMMIT_A, NOW.minus(Duration.ofHours(24)));
        assertEquals(JourneyFreshness.DELAYED, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
    }

    @Test
    void explicitStaleMarkWinsUntilNextObservation() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "WEB_REPO", COMMIT_A, NOW);
        fixture.service().markStale("ACCOUNT_OPENING", "WEB_REPO", NOW);
        assertEquals(JourneyFreshness.STALE, fixture.service().freshnessFor(MANIFEST).get("WEB_REPO"));

        fixture.service().observe("ACCOUNT_OPENING", "WEB_REPO", COMMIT_A, NOW.plusSeconds(1));
        assertEquals(JourneyFreshness.LIVE, fixture.service().freshnessFor(MANIFEST).get("WEB_REPO"));
    }
}
