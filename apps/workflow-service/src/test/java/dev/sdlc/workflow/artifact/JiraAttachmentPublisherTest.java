package dev.sdlc.workflow.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JiraAttachmentPublisherTest {

    @Test
    void recordsPendingWhenJiraProjectionFailsWithoutLosingTheArtifact() {
        AtomicInteger attempts = new AtomicInteger();
        JiraProjectionClient client = (ticketId, summary, html) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("fictional Jira outage");
        };
        JiraAttachmentPublisher publisher = new JiraAttachmentPublisher(client);

        JiraProjectionResult result = publisher.publish("DEMO-123", "Requirement ready", "<html></html>");

        assertThat(result.status()).isEqualTo(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(attempts).hasValue(1);
    }
}
