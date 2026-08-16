package dev.sdlc.workflow.journey;

public record JourneyHttpEdge(
        String edgeId,
        String caller,
        String apiRepositoryAlias,
        String method,
        String normalizedPath,
        String requestSchemaRef,
        String responseSchemaRef,
        String commonHeaderRule,
        String authenticationClass,
        String compatibility,
        JourneyProvenance provenance) { }
