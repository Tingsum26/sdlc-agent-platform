package dev.sdlc.workflow.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JourneyGapAnalyzerTest {
    private final JourneyGapAnalyzer analyzer = new JourneyGapAnalyzer();

    @Test
    void completeFictionalHybridJourneyPassesContractChecks() {
        JourneyAnalysis analysis = analyzer.analyze(completeManifest());

        assertThat(analysis.status().name()).isEqualTo("CONTRACT_PASS");
        assertThat(analysis.gaps()).isEmpty();
        assertThat(analysis.totalEdges()).isEqualTo(1);
        assertThat(analysis.provenEdges()).isEqualTo(1);
    }

    @Test
    void reportsOrderedCompatibilityReleaseOwnershipAndEvidenceGaps() {
        JourneyManifest incomplete = new JourneyManifest(
                "1.0", "ACCOUNT_OPENING", "CUSTOMER", 1,
                List.of(new JourneyRepositoryEntry("API_REPO", RepositoryRole.API, "")),
                List.of(),
                List.of(new JourneyHttpEdge("EDGE_1", "WEB_REPO", "API_REPO", "POST", "/accounts", "", "", "", "OAUTH", "BREAKING_REJECTED", null)),
                new JourneyReleasePolicy(true, "", 30, "rollback"),
                new JourneyFeatureFlag(true, "", ""),
                List.of());

        assertThat(analyzer.analyze(incomplete).gaps()).extracting(JourneyGap::code).containsExactly(
                "MISSING_REPOSITORY_WEB", "MISSING_REPOSITORY_IOS", "MISSING_REPOSITORY_ANDROID",
                "MISSING_REQUEST_SCHEMA", "MISSING_RESPONSE_SCHEMA", "MISSING_COMMON_HEADER",
                "BREAKING_CHANGE_REJECTED", "MISSING_PROVENANCE", "MISSING_NATIVE_RELEASE_TRAIN",
                "MISSING_FEATURE_FLAG", "MISSING_E2E_OWNER");
    }

    static JourneyManifest completeManifest() {
        String ref = "0123456789012345678901234567890123456789";
        return new JourneyManifest(
                "1.0", "ACCOUNT_OPENING", "CUSTOMER", 1,
                List.of(
                        new JourneyRepositoryEntry("API_REPO", RepositoryRole.API, ref),
                        new JourneyRepositoryEntry("WEB_REPO", RepositoryRole.WEB, ref),
                        new JourneyRepositoryEntry("IOS_REPO", RepositoryRole.IOS, ref),
                        new JourneyRepositoryEntry("ANDROID_REPO", RepositoryRole.ANDROID, ref)),
                List.of(new JourneyScreen("OPEN_ACCOUNT", "WEB", "WEB_REPO")),
                List.of(new JourneyHttpEdge("EDGE_1", "WEB_REPO", "API_REPO", "POST", "/accounts", "schema/request", "schema/response", "X-Company-Context", "OAUTH", "ADDITIVE_WITH_FLAG", new JourneyProvenance("CODE_SCAN", ref, "EVIDENCE_1"))),
                new JourneyReleasePolicy(true, "MONTHLY_NATIVE", 60, "disable AWS toggle"),
                new JourneyFeatureFlag(true, "AWS_APP_CONFIG", "PRODUCT_OWNER"),
                List.of(new JourneyE2EOwner("HAPPY_PATH", "QA")));
    }
}
