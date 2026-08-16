package dev.sdlc.workflow.artifact;

@FunctionalInterface
public interface JiraProjectionClient {
    void publish(String ticketId, String summary, String html);
}
