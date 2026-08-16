package dev.sdlc.workflow.integration;

public final class MockScmEventAdapter implements ScmEventAdapter {
    @Override
    public ScmEvent normalize(String eventType, String deliveryId) {
        return new ScmEvent(eventType, deliveryId, "REPO_A");
    }
}
