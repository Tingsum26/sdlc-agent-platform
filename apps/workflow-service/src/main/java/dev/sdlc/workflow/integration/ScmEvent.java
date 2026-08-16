package dev.sdlc.workflow.integration;

public record ScmEvent(String eventType, String deliveryId, String repositoryAlias) {
}
