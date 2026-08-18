package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.JiraProjectionClient;
import java.util.ArrayList;
import java.util.List;

public final class FakeJiraProjectionClient implements JiraProjectionClient {
    private final List<String> published = new ArrayList<>();
    private int failNext;

    public void failNext() {
        this.failNext++;
    }

    @Override
    public synchronized void publish(String ticketId, String summary, String html) {
        if (failNext > 0) {
            failNext--;
            throw new IllegalStateException("Fictional Jira outage");
        }
        published.add(ticketId + "|" + summary + "|" + html);
    }

    public synchronized List<String> published() {
        return List.copyOf(published);
    }
}
