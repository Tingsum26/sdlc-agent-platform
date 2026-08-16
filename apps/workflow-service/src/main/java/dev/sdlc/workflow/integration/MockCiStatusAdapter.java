package dev.sdlc.workflow.integration;

public final class MockCiStatusAdapter implements CiStatusAdapter {
    @Override
    public CiStatus getStatus(String repositoryAlias, String revision) {
        return new CiStatus(repositoryAlias, revision, CiState.PASSED,
                "https://example.invalid/ci/" + repositoryAlias + "/" + revision);
    }
}
