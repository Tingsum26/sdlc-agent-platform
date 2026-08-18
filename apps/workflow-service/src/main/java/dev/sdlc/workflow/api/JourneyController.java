package dev.sdlc.workflow.api;

import dev.sdlc.workflow.journey.JourneyAnalysis;
import dev.sdlc.workflow.journey.JourneyGapAnalyzer;
import dev.sdlc.workflow.journey.JourneyManifest;
import dev.sdlc.workflow.journey.JourneyManifestContractValidator;
import dev.sdlc.workflow.journey.JourneyReportRenderer;
import dev.sdlc.workflow.journeyfreshness.JourneyFreshness;
import dev.sdlc.workflow.journeyfreshness.JourneyFreshnessService;
import dev.sdlc.workflow.journeyfreshness.JourneyObservation;
import dev.sdlc.workflow.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journeys")
public class JourneyController {
    private final JourneyGapAnalyzer analyzer = new JourneyGapAnalyzer();
    private final JourneyReportRenderer renderer = new JourneyReportRenderer();
    private final JourneyFreshnessService freshness;

    public JourneyController(JourneyFreshnessService freshness) {
        this.freshness = freshness;
    }

    @PostMapping("/validate")
    Map<String, Object> validate(@RequestBody JourneyManifest manifest, HttpServletRequest request) {
        CurrentUser.require(request);
        requireBasicContract(manifest);
        return Map.of("valid", true, "schemaVersion", manifest.schemaVersion(), "journeyId", manifest.journeyId());
    }

    @PostMapping("/analyze")
    JourneyAnalysis analyze(@RequestBody JourneyManifest manifest, HttpServletRequest request) {
        CurrentUser.require(request);
        requireBasicContract(manifest);
        return analyzer.analyze(manifest);
    }

    @PostMapping(value = "/report", produces = MediaType.TEXT_HTML_VALUE)
    String report(@RequestBody JourneyManifest manifest, HttpServletRequest request) {
        CurrentUser.require(request);
        requireBasicContract(manifest);
        return renderer.render(analyzer.analyze(manifest), freshness.freshnessFor(manifest));
    }

    @PostMapping("/freshness")
    Map<String, JourneyFreshness> freshness(@RequestBody JourneyManifest manifest, HttpServletRequest request) {
        CurrentUser.require(request);
        requireBasicContract(manifest);
        return freshness.freshnessFor(manifest);
    }

    @PostMapping("/observations")
    ResponseEntity<JourneyObservation> observe(@RequestBody ObserveRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-JOURNEY-001 Feed real repository observation events (merge hooks) into the freshness engine.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(freshness.observe(body.journeyId(), body.repositoryAlias(), body.commit()));
    }

    @PostMapping("/observations/stale")
    JourneyObservation markStale(@RequestBody StaleRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-JOURNEY-001 Feed real repository observation events (merge hooks) into the freshness engine.
        return freshness.markStale(body.journeyId(), body.repositoryAlias());
    }

    public record ObserveRequest(String journeyId, String repositoryAlias, String commit) {
    }

    public record StaleRequest(String journeyId, String repositoryAlias) {
    }

    private static void requireBasicContract(JourneyManifest manifest) {
        new JourneyManifestContractValidator().validate(manifest);
    }
}
