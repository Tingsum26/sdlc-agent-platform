package dev.sdlc.workflow.journey;

import dev.sdlc.workflow.evidence.EvidenceStatus;
import java.util.List;

public record JourneyAnalysis(
        JourneyManifest manifest,
        EvidenceStatus status,
        List<JourneyGap> gaps,
        int totalEdges,
        int provenEdges) {
    public JourneyAnalysis {
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }
}
