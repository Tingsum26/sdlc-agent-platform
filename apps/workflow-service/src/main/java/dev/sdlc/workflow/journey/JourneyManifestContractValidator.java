package dev.sdlc.workflow.journey;

import java.util.Set;
import java.util.regex.Pattern;

public final class JourneyManifestContractValidator {
    private static final Pattern ID = Pattern.compile("^[A-Z][A-Z0-9_]{2,79}$");
    private static final Pattern EDGE_ID = Pattern.compile("^[A-Z][A-Z0-9_-]{2,79}$");
    private static final Pattern REF = Pattern.compile("^[0-9a-f]{40}$");
    private static final Set<String> CLIENTS = Set.of("WEB", "IOS", "ANDROID");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> COMPATIBILITY = Set.of("BACKWARD_COMPATIBLE", "ADDITIVE_WITH_FLAG", "BREAKING_REJECTED");

    public void validate(JourneyManifest manifest) {
        require(manifest != null, "manifest");
        require("1.0".equals(manifest.schemaVersion()), "schemaVersion");
        requireId(manifest.journeyId(), "journeyId");
        requireId(manifest.domainId(), "domainId");
        require(manifest.version() >= 1, "version");
        require(!manifest.repositories().isEmpty() && manifest.repositories().size() <= 200, "repositories");
        for (JourneyRepositoryEntry repository : manifest.repositories()) {
            require(repository != null && repository.role() != null, "repository");
            requireId(repository.alias(), "repository.alias");
            requireRef(repository.ref(), "repository.ref");
        }
        require(manifest.screens().size() <= 1000, "screens");
        for (JourneyScreen screen : manifest.screens()) {
            require(screen != null && CLIENTS.contains(screen.client()), "screen.client");
            requireId(screen.screenId(), "screen.screenId");
            requireId(screen.repositoryAlias(), "screen.repositoryAlias");
        }
        require(manifest.httpEdges().size() <= 5000, "httpEdges");
        for (JourneyHttpEdge edge : manifest.httpEdges()) validateEdge(edge);
        JourneyReleasePolicy release = manifest.releasePolicy();
        require(release != null, "releasePolicy");
        requireText(release.nativeReleaseTrain(), 80, "nativeReleaseTrain");
        require(release.compatibilityWindowDays() >= 1 && release.compatibilityWindowDays() <= 730, "compatibilityWindowDays");
        requireText(release.rollbackRule(), 300, "rollbackRule");
        JourneyFeatureFlag flag = manifest.featureFlag();
        require(flag != null, "featureFlag");
        requireText(flag.provider(), 80, "featureFlag.provider");
        requireText(flag.ownerRole(), 80, "featureFlag.ownerRole");
        require(!manifest.e2eOwners().isEmpty() && manifest.e2eOwners().size() <= 200, "e2eOwners");
        for (JourneyE2EOwner owner : manifest.e2eOwners()) {
            require(owner != null, "e2eOwner");
            requireId(owner.scenario(), "e2eOwner.scenario");
            requireText(owner.ownerRole(), 80, "e2eOwner.ownerRole");
        }
    }

    private static void validateEdge(JourneyHttpEdge edge) {
        require(edge != null && EDGE_ID.matcher(text(edge.edgeId())).matches(), "edgeId");
        requireId(edge.caller(), "edge.caller");
        requireId(edge.apiRepositoryAlias(), "edge.apiRepositoryAlias");
        require(METHODS.contains(edge.method()), "edge.method");
        require(edge.normalizedPath() != null && edge.normalizedPath().startsWith("/") && edge.normalizedPath().length() <= 300, "edge.normalizedPath");
        requireText(edge.requestSchemaRef(), 300, "edge.requestSchemaRef");
        requireText(edge.responseSchemaRef(), 300, "edge.responseSchemaRef");
        requireText(edge.commonHeaderRule(), 120, "edge.commonHeaderRule");
        requireText(edge.authenticationClass(), 80, "edge.authenticationClass");
        require(COMPATIBILITY.contains(edge.compatibility()), "edge.compatibility");
        JourneyProvenance provenance = edge.provenance();
        require(provenance != null, "edge.provenance");
        requireId(provenance.source(), "provenance.source");
        requireRef(provenance.ref(), "provenance.ref");
        require(EDGE_ID.matcher(text(provenance.evidenceId())).matches(), "provenance.evidenceId");
    }

    private static void requireId(String value, String field) { require(ID.matcher(text(value)).matches(), field); }
    private static void requireRef(String value, String field) { require(REF.matcher(text(value)).matches(), field); }
    private static void requireText(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.length() <= max, field);
    }
    private static String text(String value) { return value == null ? "" : value; }
    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("Journey manifest violates v1 field: " + field);
    }
}
