package dev.sdlc.workflow.integration;

public interface ScmEventAdapter {
    ScmEvent normalize(String eventType, String deliveryId);
}
