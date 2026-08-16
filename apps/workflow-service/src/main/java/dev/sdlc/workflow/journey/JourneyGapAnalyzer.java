package dev.sdlc.workflow.journey;

import dev.sdlc.workflow.evidence.EvidenceStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class JourneyGapAnalyzer {
    private static final Set<RepositoryRole> REQUIRED_ROLES = EnumSet.of(
            RepositoryRole.API, RepositoryRole.WEB, RepositoryRole.IOS, RepositoryRole.ANDROID);

    public JourneyAnalysis analyze(JourneyManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("Journey manifest is required");
        List<JourneyGap> gaps = new ArrayList<>();
        Set<RepositoryRole> present = EnumSet.noneOf(RepositoryRole.class);
        manifest.repositories().stream().map(JourneyRepositoryEntry::role).filter(java.util.Objects::nonNull).forEach(present::add);
        for (RepositoryRole role : REQUIRED_ROLES) {
            if (!present.contains(role)) add(gaps, "MISSING_REPOSITORY_" + role, "BLOCKER", "Required " + role + " repository is not declared.");
        }
        int proven = 0;
        for (JourneyHttpEdge edge : manifest.httpEdges()) {
            if (blank(edge.requestSchemaRef())) add(gaps, "MISSING_REQUEST_SCHEMA", "BLOCKER", edge.edgeId());
            if (blank(edge.responseSchemaRef())) add(gaps, "MISSING_RESPONSE_SCHEMA", "BLOCKER", edge.edgeId());
            if (blank(edge.commonHeaderRule())) add(gaps, "MISSING_COMMON_HEADER", "BLOCKER", edge.edgeId());
            if ("BREAKING_REJECTED".equals(edge.compatibility())) add(gaps, "BREAKING_CHANGE_REJECTED", "BLOCKER", edge.edgeId());
            if (edge.provenance() == null || blank(edge.provenance().evidenceId()) || blank(edge.provenance().ref())) {
                add(gaps, "MISSING_PROVENANCE", "BLOCKER", edge.edgeId());
            } else {
                proven++;
            }
        }
        if (manifest.releasePolicy() == null || blank(manifest.releasePolicy().nativeReleaseTrain()))
            add(gaps, "MISSING_NATIVE_RELEASE_TRAIN", "BLOCKER", "Native release timing is required.");
        if (manifest.featureFlag() == null || (manifest.featureFlag().required()
                && (blank(manifest.featureFlag().provider()) || blank(manifest.featureFlag().ownerRole()))))
            add(gaps, "MISSING_FEATURE_FLAG", "BLOCKER", "Required feature flag provider and owner are missing.");
        if (manifest.e2eOwners().isEmpty()) add(gaps, "MISSING_E2E_OWNER", "BLOCKER", "At least one manual E2E owner is required.");
        EvidenceStatus status = gaps.isEmpty() ? EvidenceStatus.CONTRACT_PASS : EvidenceStatus.INTERNAL_VALIDATION_REQUIRED;
        return new JourneyAnalysis(manifest, status, gaps, manifest.httpEdges().size(), proven);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void add(List<JourneyGap> gaps, String code, String severity, String detail) {
        if (gaps.stream().noneMatch(gap -> gap.code().equals(code))) gaps.add(new JourneyGap(code, severity, detail));
    }
}
