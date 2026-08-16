package dev.sdlc.workflow.integration;

public record GhesCheckSummary(String repositoryAlias, String revision, CiState state, String nextCursor) {
}
