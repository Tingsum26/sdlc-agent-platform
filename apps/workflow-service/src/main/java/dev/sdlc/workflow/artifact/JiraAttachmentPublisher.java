package dev.sdlc.workflow.artifact;

public final class JiraAttachmentPublisher {

    private final JiraProjectionClient client;

    public JiraAttachmentPublisher(JiraProjectionClient client) {
        this.client = client;
    }

    public JiraProjectionResult publish(String ticketId, String summary, String html) {
        try {
            client.publish(ticketId, summary, html);
            return new JiraProjectionResult(JiraProjectionStatus.PUBLISHED, 1);
        } catch (RuntimeException exception) {
            return new JiraProjectionResult(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, 1);
        }
    }
}
