package dev.sdlc.workflow.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JourneyReportRendererTest {
    @Test
    void rendersStandaloneAccessibleReportAndEscapesLabels() {
        JourneyAnalysis analysis = new JourneyAnalysis(
                JourneyGapAnalyzerTest.completeManifest(),
                dev.sdlc.workflow.evidence.EvidenceStatus.INTERNAL_VALIDATION_REQUIRED,
                List.of(new JourneyGap("MISSING_PROOF", "BLOCKER", "<script>alert(1)</script>")), 1, 0);

        String html = new JourneyReportRenderer().render(analysis);

        assertThat(html).contains("<!doctype html>", "INTERNAL_VALIDATION_REQUIRED", "aria-label", "Evidence status");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)</script>");
    }
}
