package dev.sdlc.workflow.jiraprojection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.artifact.JiraProjectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JiraProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private record Fixture(JiraProjectionService service, FakeJiraProjectionClient client) {
    }

    private Fixture fixture() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FakeJiraProjectionClient client = new FakeJiraProjectionClient();
        JiraProjectionService service = new JiraProjectionService(new InMemoryJiraProjectionRepository(),
                client, clock);
        return new Fixture(service, client);
    }

    @Test
    void enqueueStartsPendingAndFlushPublishes() {
        Fixture fixture = fixture();
        JiraProjection draft = fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");
        assertEquals(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, draft.status());
        assertEquals(0, draft.attempts());

        JiraProjection published = fixture.service().flushPending("EMP-100", "corr-2").get(0);
        assertEquals(JiraProjectionStatus.PUBLISHED, published.status());
        assertEquals(1, published.attempts());
        assertEquals(1, fixture.client().published().size());
    }

    @Test
    void enqueueIsIdempotentPerTicketAndMilestone() {
        Fixture fixture = fixture();
        fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");
        JiraProjection again = fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Different summary", "EMP-100", "corr-2");
        assertEquals("Requirement approved", again.summary());
        assertEquals(1, fixture.service().listAll().size());
    }

    @Test
    void failingClientStaysPendingAndFailsAfterMaxAttempts() {
        Fixture fixture = fixture();
        fixture.client().failNext();
        fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");

        JiraProjection afterFirst = fixture.service().flushPending("EMP-100", "corr-2").get(0);
        assertEquals(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, afterFirst.status());
        assertEquals(1, afterFirst.attempts());

        fixture.client().failNext();
        fixture.client().failNext();
        fixture.service().flushPending("EMP-100", "corr-3");
        JiraProjection afterThird = fixture.service().flushPending("EMP-100", "corr-4").get(0);
        assertEquals(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_FAILED, afterThird.status());
        assertEquals(3, afterThird.attempts());
    }

    @Test
    void flushSkipsPublishedProjections() {
        Fixture fixture = fixture();
        fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");
        fixture.service().flushPending("EMP-100", "corr-2");
        assertEquals(0, fixture.service().flushPending("EMP-100", "corr-3").size());
    }
}
