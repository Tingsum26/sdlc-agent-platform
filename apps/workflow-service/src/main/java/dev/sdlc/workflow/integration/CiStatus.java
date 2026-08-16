package dev.sdlc.workflow.integration;

public record CiStatus(String repositoryAlias, String revision, CiState state, String detailsUrl) {
}
