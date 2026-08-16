package dev.sdlc.workflow.integration;

public interface CiStatusAdapter {
    CiStatus getStatus(String repositoryAlias, String revision);
}
