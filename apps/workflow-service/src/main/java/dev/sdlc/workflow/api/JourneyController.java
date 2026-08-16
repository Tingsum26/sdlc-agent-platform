package dev.sdlc.workflow.api;

import dev.sdlc.workflow.journey.JourneyAnalysis;
import dev.sdlc.workflow.journey.JourneyGapAnalyzer;
import dev.sdlc.workflow.journey.JourneyManifest;
import dev.sdlc.workflow.journey.JourneyReportRenderer;
import dev.sdlc.workflow.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journeys")
public class JourneyController {
    private final JourneyGapAnalyzer analyzer = new JourneyGapAnalyzer();
    private final JourneyReportRenderer renderer = new JourneyReportRenderer();

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
        return renderer.render(analyzer.analyze(manifest));
    }

    private static void requireBasicContract(JourneyManifest manifest) {
        if (manifest == null || !"1.0".equals(manifest.schemaVersion()) || manifest.journeyId() == null
                || manifest.journeyId().isBlank() || manifest.repositories().size() > 200
                || manifest.screens().size() > 1000 || manifest.httpEdges().size() > 5000) {
            throw new IllegalArgumentException("Journey manifest violates the v1 size or identity contract");
        }
    }
}
