package dev.sdlc.workflow.task;

import java.util.Objects;

public record WorkflowScope(String ticketId, String repositoryAlias, String targetCommit) {

    public WorkflowScope {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(repositoryAlias, "repositoryAlias");
        Objects.requireNonNull(targetCommit, "targetCommit");
    }
}
