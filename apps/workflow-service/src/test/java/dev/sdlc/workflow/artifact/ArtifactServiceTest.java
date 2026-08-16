package dev.sdlc.workflow.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactServiceTest {

    private ArtifactService service;

    @BeforeEach
    void setUp() {
        service = new ArtifactService(
                new FakeArtifactStore(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsAHashedStructuredArtifactAndRendersEscapedHtml() {
        ArtifactMetadata artifact = service.create("ART-001", "TASK-001", ArtifactType.REQUIREMENT_REPORT,
                List.of(new ArtifactSection("summary", "Requirement <One>", "Use **safe** evidence & review.")),
                "developer-1", null);

        assertThat(artifact.version()).isEqualTo(1);
        assertThat(artifact.contentHash()).matches("[a-f0-9]{64}");
        assertThat(service.renderHtml(artifact.artifactId(), artifact.version()))
                .contains("Requirement &lt;One&gt;")
                .contains("evidence &amp; review")
                .doesNotContain("<script");
    }

    @Test
    void rejectsExecutableContent() {
        assertThatThrownBy(() -> service.create("ART-002", "TASK-001", ArtifactType.REQUIREMENT_REPORT,
                List.of(new ArtifactSection("summary", "Unsafe", "<script>alert(1)</script>")),
                "developer-1", null))
                .isInstanceOf(UnsafeArtifactContentException.class);
    }

    @Test
    void rejectsAClientHashThatDoesNotMatchCanonicalContent() {
        assertThatThrownBy(() -> service.create("ART-003", "TASK-001", ArtifactType.REQUIREMENT_REPORT,
                List.of(new ArtifactSection("summary", "Summary", "Evidence")),
                "developer-1", "0".repeat(64)))
                .isInstanceOf(ArtifactHashMismatchException.class);
    }

    @Test
    void preventsReplacingAnApprovedArtifactVersion() {
        ArtifactMetadata artifact = service.create("ART-004", "TASK-001", ArtifactType.REQUIREMENT_REPORT,
                List.of(new ArtifactSection("summary", "Summary", "Evidence")),
                "developer-1", null);
        service.markApproved(artifact.artifactId(), artifact.version(), "approver-1");

        assertThatThrownBy(() -> service.create("ART-004", "TASK-001", ArtifactType.REQUIREMENT_REPORT,
                List.of(new ArtifactSection("summary", "Changed", "Changed evidence")),
                "developer-1", null))
                .isInstanceOf(ArtifactImmutableException.class);
    }
}
