# M4 Journey/Repo Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make milestone M4 runnable: a Journey freshness engine (`LIVE/DELAYED/STALE/OFFLINE`), freshness-badged HTML Journey report, fictitious Account Opening sample data, and a browser E2E that opens the report, verifies evidence badges, marks one repository stale, and refreshes the flow.

**Architecture:** New `journeyfreshness` domain: `JourneyFreshness` enum, `JourneyObservation` record, interface + in-memory repository, and `JourneyFreshnessService` (observe / markStale / freshnessFor) with a deterministic `Clock`. `JourneyReportRenderer` gains a `render(analysis, freshnessByRepository)` overload that emits per-repository freshness badges. `JourneyController` gains three endpoints (`POST /journeys/freshness`, `POST /journeys/observations`, `POST /journeys/observations/stale`) and the report endpoint returns the badged report. The Web demo loads a fictitious Account Opening manifest from a fixture file and gains an M4 panel (observe API repo → LIVE, mark WEB repo stale → STALE, refresh report).

**Tech Stack:** Java 17 Spring Boot (existing), React 19 + Vite (existing), Playwright (existing).

**Working directory:** `D:\codex\sdlc-agent-platform\.worktrees\agent-mvp-vertical-slice`

**Existing seed (read first):** `dev.sdlc.workflow.journey` — `JourneyManifest` (repositories are `JourneyRepositoryEntry(alias, role, ref)`; httpEdges carry `requestSchemaRef/responseSchemaRef/commonHeaderRule/compatibility/provenance`), `JourneyGapAnalyzer` → `JourneyAnalysis(manifest, status, gaps, totalEdges, provenEdges)`, `JourneyReportRenderer.render(JourneyAnalysis)`, `JourneyController` (`/validate`, `/analyze`, `/report`).

---

### Task 1: Journey freshness domain and service

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journeyfreshness/JourneyFreshness.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journeyfreshness/JourneyObservation.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journeyfreshness/JourneyObservationRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journeyfreshness/InMemoryJourneyObservationRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journeyfreshness/JourneyFreshnessService.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/journeyfreshness/JourneyFreshnessServiceTest.java`

- [ ] **Step 1: Write the failing test**

`apps/workflow-service/src/test/java/dev/sdlc/workflow/journeyfreshness/JourneyFreshnessServiceTest.java`:

```java
package dev.sdlc.workflow.journeyfreshness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.journey.JourneyManifest;
import dev.sdlc.workflow.journey.JourneyRepositoryEntry;
import dev.sdlc.workflow.journey.RepositoryRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JourneyFreshnessServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final String COMMIT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String COMMIT_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final JourneyManifest MANIFEST = new JourneyManifest("1.0", "ACCOUNT_OPENING", "CUSTOMER", 1,
            List.of(
                    new JourneyRepositoryEntry("API_REPO", RepositoryRole.API, COMMIT_A),
                    new JourneyRepositoryEntry("WEB_REPO", RepositoryRole.WEB, COMMIT_A)),
            List.of(), List.of(), null, null, List.of());

    private record Fixture(JourneyFreshnessService service, InMemoryJourneyObservationRepository repository) {
    }

    private Fixture fixture() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryJourneyObservationRepository repository = new InMemoryJourneyObservationRepository();
        return new Fixture(new JourneyFreshnessService(repository, clock), repository);
    }

    @Test
    void unobservedRepositoriesAreOffline() {
        Fixture fixture = fixture();
        assertEquals(JourneyFreshness.OFFLINE, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
        assertEquals(JourneyFreshness.OFFLINE, fixture.service().freshnessFor(MANIFEST).get("WEB_REPO"));
    }

    @Test
    void matchingRecentObservationIsLive() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "API_REPO", COMMIT_A, NOW);
        assertEquals(JourneyFreshness.LIVE, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
    }

    @Test
    void mismatchedRecentObservationIsStale() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "API_REPO", COMMIT_B, NOW);
        assertEquals(JourneyFreshness.STALE, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
    }

    @Test
    void oldObservationIsDelayed() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "API_REPO", COMMIT_A, NOW.minus(Duration.ofHours(24)));
        assertEquals(JourneyFreshness.DELAYED, fixture.service().freshnessFor(MANIFEST).get("API_REPO"));
    }

    @Test
    void explicitStaleMarkWinsUntilNextObservation() {
        Fixture fixture = fixture();
        fixture.service().observe("ACCOUNT_OPENING", "WEB_REPO", COMMIT_A, NOW);
        fixture.service().markStale("ACCOUNT_OPENING", "WEB_REPO", NOW);
        assertEquals(JourneyFreshness.STALE, fixture.service().freshnessFor(MANIFEST).get("WEB_REPO"));

        fixture.service().observe("ACCOUNT_OPENING", "WEB_REPO", COMMIT_A, NOW.plusSeconds(1));
        assertEquals(JourneyFreshness.LIVE, fixture.service().freshnessFor(MANIFEST).get("WEB_REPO"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JourneyFreshnessServiceTest test`
Expected: COMPILATION FAILURE — missing classes.

- [ ] **Step 3: Implement the domain**

`JourneyFreshness.java`:

```java
package dev.sdlc.workflow.journeyfreshness;

public enum JourneyFreshness { LIVE, DELAYED, STALE, OFFLINE }
```

`JourneyObservation.java`:

```java
package dev.sdlc.workflow.journeyfreshness;

import java.time.Instant;
import java.util.Objects;

public record JourneyObservation(
        String journeyId,
        String repositoryAlias,
        String commit,
        Instant observedAt,
        boolean staleMarked) {

    public JourneyObservation {
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(repositoryAlias, "repositoryAlias");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
```

`JourneyObservationRepository.java`:

```java
package dev.sdlc.workflow.journeyfreshness;

import java.util.List;
import java.util.Optional;

public interface JourneyObservationRepository {
    Optional<JourneyObservation> find(String journeyId, String repositoryAlias);
    JourneyObservation save(JourneyObservation observation);
    List<JourneyObservation> findAll();
}
```

`InMemoryJourneyObservationRepository.java`:

```java
package dev.sdlc.workflow.journeyfreshness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryJourneyObservationRepository implements JourneyObservationRepository {
    private final ConcurrentMap<String, JourneyObservation> observations = new ConcurrentHashMap<>();

    @Override
    public Optional<JourneyObservation> find(String journeyId, String repositoryAlias) {
        return Optional.ofNullable(observations.get(key(journeyId, repositoryAlias)));
    }

    @Override
    public JourneyObservation save(JourneyObservation observation) {
        observations.put(key(observation.journeyId(), observation.repositoryAlias()), observation);
        return observation;
    }

    @Override
    public List<JourneyObservation> findAll() {
        return new ArrayList<>(observations.values());
    }

    private static String key(String journeyId, String repositoryAlias) {
        return journeyId + ":" + repositoryAlias;
    }
}
```

`JourneyFreshnessService.java`:

```java
package dev.sdlc.workflow.journeyfreshness;

import dev.sdlc.workflow.journey.JourneyManifest;
import dev.sdlc.workflow.journey.JourneyRepositoryEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JourneyFreshnessService {

    private static final Duration LIVE_WINDOW = Duration.ofHours(12);

    private final JourneyObservationRepository observations;
    private final Clock clock;

    public JourneyFreshnessService(JourneyObservationRepository observations, Clock clock) {
        this.observations = observations;
        this.clock = clock;
    }

    public JourneyObservation observe(String journeyId, String repositoryAlias, String commit) {
        if (journeyId == null || journeyId.isBlank()) throw new IllegalArgumentException("journeyId is required");
        if (repositoryAlias == null || repositoryAlias.isBlank()) {
            throw new IllegalArgumentException("repositoryAlias is required");
        }
        if (commit == null || commit.isBlank()) throw new IllegalArgumentException("commit is required");
        JourneyObservation observation = new JourneyObservation(journeyId, repositoryAlias, commit,
                clock.instant(), false);
        return observations.save(observation);
    }

    public JourneyObservation markStale(String journeyId, String repositoryAlias) {
        if (journeyId == null || journeyId.isBlank()) throw new IllegalArgumentException("journeyId is required");
        if (repositoryAlias == null || repositoryAlias.isBlank()) {
            throw new IllegalArgumentException("repositoryAlias is required");
        }
        JourneyObservation existing = observations.find(journeyId, repositoryAlias)
                .orElseGet(() -> new JourneyObservation(journeyId, repositoryAlias, "", clock.instant(), false));
        return observations.save(new JourneyObservation(journeyId, repositoryAlias, existing.commit(),
                clock.instant(), true));
    }

    public Map<String, JourneyFreshness> freshnessFor(JourneyManifest manifest) {
        Map<String, JourneyFreshness> result = new LinkedHashMap<>();
        if (manifest == null) return result;
        Instant now = clock.instant();
        for (JourneyRepositoryEntry entry : manifest.repositories()) {
            result.put(entry.alias(), freshness(entry.alias(), entry.ref(), now));
        }
        return result;
    }

    private JourneyFreshness freshness(String repositoryAlias, String declaredRef, Instant now) {
        JourneyObservation observation = observations.find(null, repositoryAlias).orElse(null);
        if (observation == null) return JourneyFreshness.OFFLINE;
        if (observation.staleMarked()) return JourneyFreshness.STALE;
        if (observation.observedAt().isBefore(now.minus(LIVE_WINDOW))) return JourneyFreshness.DELAYED;
        return observation.commit().equals(declaredRef) ? JourneyFreshness.LIVE : JourneyFreshness.STALE;
    }
}
```

Note: the plan's `find(null, repositoryAlias)` is a deliberate simplification that does NOT work with the repository keyed on `journeyId:repositoryAlias` — the fix is to key lookups by the manifest's journey: the service receives only the manifest, so derive `journeyId = manifest.journeyId()` and use `observations.find(manifest.journeyId(), repositoryAlias)`. Apply that correction in `freshness(...)` by passing the journeyId down from `freshnessFor`. Update the implementation accordingly (change `freshness(String repositoryAlias, String declaredRef, Instant now)` to also take `String journeyId`).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JourneyFreshnessServiceTest test`
Expected: BUILD SUCCESS (5 tests).

- [ ] **Step 5: Run the full Java suite**

Run: `.\mvnw.cmd -q verify`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/journeyfreshness apps/workflow-service/src/test/java/dev/sdlc/workflow/journeyfreshness
git commit -m "feat(m4): add the journey freshness engine"
```

---

### Task 2: Freshness endpoints, badged report, wiring, IT

**Files:**
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyReportRenderer.java` (add `render(JourneyAnalysis, Map<String, JourneyFreshness>)` overload with per-repository badges)
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/JourneyController.java` (inject `JourneyFreshnessService`; add 3 endpoints; report endpoint uses the badged render)
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java` and `MongoRuntimeConfiguration.java` (add `journeyObservationRepository` + `journeyFreshnessService` beans with `TODO(INTERNAL): INTERNAL-JOURNEY-001`)
- Modify: `docs/handoff/INTERNAL_TODO.md` (append `INTERNAL-JOURNEY-001` row)
- Create: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JourneyFreshnessIT.java`

- [ ] **Step 1: Write the failing IT**

`apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JourneyFreshnessIT.java`:

```java
package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fake")
class JourneyFreshnessIT {

    @Autowired
    private MockMvc mvc;

    private static final String COMMIT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String COMMIT_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void observesAndMarksStaleWithFreshnessReflected() throws Exception {
        mvc.perform(post("/api/v1/journeys/observations")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"ACCOUNT_OPENING\",\"repositoryAlias\":\"API_REPO\",\"commit\":\"" + COMMIT_A + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repositoryAlias").value("API_REPO"));

        mvc.perform(post("/api/v1/journeys/observations/stale")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"ACCOUNT_OPENING\",\"repositoryAlias\":\"WEB_REPO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staleMarked").value(true));

        String manifest = """
                {
                  "schemaVersion":"1.0",
                  "journeyId":"ACCOUNT_OPENING",
                  "domainId":"CUSTOMER",
                  "version":1,
                  "repositories":[
                    {"alias":"API_REPO","role":"API","ref":"%s"},
                    {"alias":"WEB_REPO","role":"WEB","ref":"%s"}
                  ],
                  "screens":[],"httpEdges":[],
                  "releasePolicy":{"webApiFirst":true,"nativeReleaseTrain":"MONTHLY_NATIVE","compatibilityWindowDays":60,"rollbackRule":"disable AWS toggle"},
                  "featureFlag":{"required":true,"provider":"AWS_APP_CONFIG","ownerRole":"PRODUCT_OWNER"},
                  "e2eOwners":[]
                }
                """.formatted(COMMIT_A, COMMIT_A);

        mvc.perform(post("/api/v1/journeys/freshness")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.API_REPO").value("LIVE"))
                .andExpect(jsonPath("$.WEB_REPO").value("STALE"));

        mvc.perform(post("/api/v1/journeys/report")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifest))
                .andExpect(status().isOk())
                .andExpect(org.hamcrest.Matchers.containsString("LIVE"))
                .andExpect(org.hamcrest.Matchers.containsString("STALE"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JourneyFreshnessIT test`
Expected: FAIL — 404/500 on the new endpoints.

- [ ] **Step 3: Implement the renderer overload**

In `JourneyReportRenderer.java` add:

```java
    public String render(JourneyAnalysis analysis, java.util.Map<String, dev.sdlc.workflow.journeyfreshness.JourneyFreshness> freshnessByRepository) {
        StringBuilder freshness = new StringBuilder();
        if (freshnessByRepository == null || freshnessByRepository.isEmpty()) {
            freshness.append("<li>No repositories observed yet — freshness is OFFLINE.</li>");
        } else {
            freshnessByRepository.forEach((alias, value) ->
                    freshness.append("<li><strong>").append(escape(alias)).append("</strong> — ").append(value).append("</li>"));
        }
        String base = render(analysis);
        return base.replace(
                "<section aria-labelledby=\"coverage\">",
                "<section aria-labelledby=\"freshness\"><h2 id=\"freshness\">Repository freshness</h2><ul>" + freshness + "</ul></section>"
                        + "<section aria-labelledby=\"coverage\">");
    }
```

Note: reusing `render(analysis)` then string-inserting is simple but brittle; if it proves awkward, rewrite `render` to accept an optional freshness section parameter instead (single `render(analysis, freshnessByRepository)` with a `null`-safe branch). Choose the cleaner option at implementation time and note it.

- [ ] **Step 4: Implement the controller additions**

In `JourneyController.java`, inject `JourneyFreshnessService freshness` (constructor) and add:

```java
    @PostMapping("/freshness")
    Map<String, dev.sdlc.workflow.journeyfreshness.JourneyFreshness> freshness(
            @RequestBody JourneyManifest manifest, HttpServletRequest request) {
        CurrentUser.require(request);
        requireBasicContract(manifest);
        return freshness.freshnessFor(manifest);
    }

    @PostMapping("/observations")
    org.springframework.http.ResponseEntity<dev.sdlc.workflow.journeyfreshness.JourneyObservation> observe(
            @RequestBody ObserveRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-JOURNEY-001 Feed real repository observation events (merge hooks) into the freshness engine.
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(freshness.observe(body.journeyId(), body.repositoryAlias(), body.commit()));
    }

    @PostMapping("/observations/stale")
    dev.sdlc.workflow.journeyfreshness.JourneyObservation markStale(
            @RequestBody StaleRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-JOURNEY-001 Feed real repository observation events (merge hooks) into the freshness engine.
        return freshness.markStale(body.journeyId(), body.repositoryAlias());
    }
```

Change the report endpoint to `return renderer.render(analyzer.analyze(manifest), freshness.freshnessFor(manifest));` and add records:

```java
    public record ObserveRequest(String journeyId, String repositoryAlias, String commit) {
    }

    public record StaleRequest(String journeyId, String repositoryAlias) {
    }
```

(Add `@NotBlank` validation imports consistent with the other controllers if the codebase style uses them; minimal is acceptable here since `JourneyFreshnessService` validates.)

- [ ] **Step 5: Wire beans in both configs**

Add imports + beans to both `FakeRuntimeConfiguration` and `MongoRuntimeConfiguration`:

```java
import dev.sdlc.workflow.journeyfreshness.InMemoryJourneyObservationRepository;
import dev.sdlc.workflow.journeyfreshness.JourneyObservationRepository;
import dev.sdlc.workflow.journeyfreshness.JourneyFreshnessService;
```

```java
    @Bean
    JourneyObservationRepository journeyObservationRepository() {
        // TODO(INTERNAL): INTERNAL-JOURNEY-001 Persist journey observations in MongoDB and feed merge hooks.
        return new InMemoryJourneyObservationRepository();
    }

    @Bean
    JourneyFreshnessService journeyFreshnessService(JourneyObservationRepository observations, Clock clock) {
        return new JourneyFreshnessService(observations, clock);
    }
```

- [ ] **Step 6: Update the registry**

Append to `docs/handoff/INTERNAL_TODO.md`:

```markdown
| INTERNAL-JOURNEY-001 | workflow-service | `api/JourneyController.java`, `config/*RuntimeConfiguration.java` | Feed real repository observation events (merge hooks) into the freshness engine and persist observations in MongoDB | Sanitized observation log | Revert to in-memory observations |
```

- [ ] **Step 7: Run tests**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JourneyFreshnessIT test`
Expected: BUILD SUCCESS. Then `.\mvnw.cmd -q verify` — BUILD SUCCESS.

- [ ] **Step 8: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyReportRenderer.java apps/workflow-service/src/main/java/dev/sdlc/workflow/api/JourneyController.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JourneyFreshnessIT.java docs/handoff/INTERNAL_TODO.md
git commit -m "feat(m4): expose journey freshness and badge the HTML report"
```

---

### Task 3: Fictitious Account Opening fixture and Web UI panel

**Files:**
- Create: `apps/web-ui/public/fixtures/journey-account-opening.json`
- Modify: `apps/web-ui/src/App.tsx`
- Test: existing `apps/web-ui/src/App.test.tsx` and `podCsv.test.ts` must stay green

- [ ] **Step 1: Create the fixture**

`apps/web-ui/public/fixtures/journey-account-opening.json` (fictitious, matches the `JourneyManifest` contract used by `/api/v1/journeys/analyze|report|freshness`):

```json
{
  "schemaVersion": "1.0",
  "journeyId": "ACCOUNT_OPENING",
  "domainId": "CUSTOMER",
  "version": 1,
  "repositories": [
    { "alias": "API_REPO", "role": "API", "ref": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" },
    { "alias": "WEB_REPO", "role": "WEB", "ref": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" },
    { "alias": "IOS_REPO", "role": "IOS", "ref": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" },
    { "alias": "ANDROID_REPO", "role": "ANDROID", "ref": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
  ],
  "screens": [
    { "screenId": "OPEN_ACCOUNT", "client": "WEB", "repositoryAlias": "WEB_REPO" },
    { "screenId": "IDENTITY_DETAILS", "client": "IOS", "repositoryAlias": "IOS_REPO" }
  ],
  "httpEdges": [
    {
      "edgeId": "EDGE_1",
      "caller": "WEB_REPO",
      "apiRepositoryAlias": "API_REPO",
      "method": "POST",
      "normalizedPath": "/accounts",
      "requestSchemaRef": "schema/request",
      "responseSchemaRef": "schema/response",
      "commonHeaderRule": "X-Company-Context",
      "authenticationClass": "OAUTH",
      "compatibility": "ADDITIVE_WITH_FLAG",
      "provenance": { "source": "CODE_SCAN", "ref": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "evidenceId": "EVIDENCE_1" }
    }
  ],
  "releasePolicy": { "webApiFirst": true, "nativeReleaseTrain": "MONTHLY_NATIVE", "compatibilityWindowDays": 60, "rollbackRule": "disable AWS toggle" },
  "featureFlag": { "required": true, "provider": "AWS_APP_CONFIG", "ownerRole": "PRODUCT_OWNER" },
  "e2eOwners": [ { "scenario": "HAPPY_PATH", "ownerRole": "QA" } ]
}
```

(Verify the exact `JourneyScreen`/`JourneyHttpEdge`/`JourneyReleasePolicy`/`JourneyFeatureFlag`/`JourneyE2EOwner` record fields from the existing `journey` package before writing the file; adapt the JSON keys to the real record component names if they differ — the existing `App.tsx` `journeyManifest` constant is the reference for the exact shape.)

- [ ] **Step 2: Add the M4 UI section**

In `apps/web-ui/src/App.tsx`:
1. Add a `const journeyManifestFixture = ...` loaded from the fixture: keep the existing inline `journeyManifest` const as-is (used by the readiness section), and add a new handler `loadJourneyFixture` that fetches `/fixtures/journey-account-opening.json`, stores it in a new state `journeyFixture`.
2. Add state: `journeyFixture`, `journeyFreshness` (Record<string,string>), `observeLine`.
3. Add handlers:
   - `observeApiRepo`: POST `/api/v1/journeys/observations` with `{journeyId:"ACCOUNT_OPENING", repositoryAlias:"API_REPO", commit:"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}` → set observeLine `Observed API_REPO · LIVE expected`.
   - `markWebRepoStale`: POST `/api/v1/journeys/observations/stale` with `{journeyId:"ACCOUNT_OPENING", repositoryAlias:"WEB_REPO"}` → set observeLine `Marked WEB_REPO stale`.
   - `refreshJourneyReport`: fetch the fixture (if not loaded), POST `/api/v1/journeys/freshness` with the fixture → set `journeyFreshness`; POST `/api/v1/journeys/report` with the fixture → set `reportHtml` (reuse the existing `reportHtml` state used by the readiness section, or a new `journeyReportHtml` state).
4. Add an M4 section after the M3 section with buttons: "Load Account Opening journey", "Observe API_REPO (LIVE)", "Mark WEB_REPO stale", "Refresh journey report"; display the freshness map as a table (alias → freshness badge) and the report iframe (reuse the `journey-report` iframe styling) when refreshed.

- [ ] **Step 3: Verify tests and build**

Run: `pnpm --filter @sdlc/web-ui test && pnpm --filter @sdlc/web-ui build`
Expected: PASS then build success (App.test.tsx must stay green; adjust only if it asserts removed text).

- [ ] **Step 4: Commit**

```powershell
git add apps/web-ui/public/fixtures/journey-account-opening.json apps/web-ui/src/App.tsx
git commit -m "feat(m4): load the fictitious Account Opening journey and show freshness badges"
```

---

### Task 4: M4 browser E2E, gates, evidence

**Files:**
- Create: `e2e/m4-journey-onboarding.spec.ts`
- Modify: `package.json` (root)
- Create: `docs/verification/m4-milestone-2026-08-18.md`

- [ ] **Step 1: Write the E2E**

`e2e/m4-journey-onboarding.spec.ts`:

```ts
import { expect, test } from "@playwright/test";

test("M4: Account Opening journey report shows evidence badges and refreshes after staleness", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Load Account Opening journey" }).click();
  await page.getByRole("button", { name: "Observe API_REPO (LIVE)" }).click();
  await expect(page.getByText(/Observed API_REPO/)).toBeVisible();
  await page.getByRole("button", { name: "Mark WEB_REPO stale" }).click();
  await expect(page.getByText(/Marked WEB_REPO stale/)).toBeVisible();

  await page.getByRole("button", { name: "Refresh journey report" }).click();
  await expect(page.getByText("API_REPO — LIVE")).toBeVisible();
  await expect(page.getByText("WEB_REPO — STALE")).toBeVisible();
  await expect(page.getByTitle("Journey readiness HTML report")).toBeVisible();
  await expect(page.getByTitle("Journey readiness HTML report")).toContainText("Evidence status: CONTRACT_PASS");
});
```

- [ ] **Step 2: Register the script**

In root `package.json` add after `"e2e:m3"`:

```json
    "e2e:m4": "playwright test e2e/m4-journey-onboarding.spec.ts"
```

- [ ] **Step 3: Run the E2E until green**

Run: `pnpm e2e:m4` (own invocation) — expect 1 passed. Then `pnpm e2e:m1`, `pnpm e2e:m2`, `pnpm e2e:m3`, `pnpm e2e:public-mvp` separately — expect 1 passed each.

- [ ] **Step 4: Full gates**

```powershell
.\mvnw.cmd -q verify
pnpm install --frozen-lockfile
pnpm test
pnpm build
```
Then lifecycle unpiped: `powershell -File scripts/start-demo.ps1` → "Public demo ready"; `powershell -File scripts/stop-demo.ps1` → ports released. Then the two static scans (TODO/TBD excluding `TODO(INTERNAL)`; credentials) — expect no output.

- [ ] **Step 5: Evidence doc + commit**

Create `docs/verification/m4-milestone-2026-08-18.md` mirroring the M3 doc: gate table, the M4 commit list (`git log --oneline ebdd6fd..HEAD` minus the evidence commit), new `TODO(INTERNAL)` IDs (`INTERNAL-JOURNEY-001`), quirks. Then:

```powershell
git add e2e/m4-journey-onboarding.spec.ts package.json docs/verification/m4-milestone-2026-08-18.md
git commit -m "test(m4): add journey freshness E2E and milestone evidence"
```

---

## Self-review notes

- Spec coverage: freshness engine (Task 1), badged report + endpoints + registry (Task 2), fixture + UI panel (Task 3), E2E + gates + evidence (Task 4). All M4 spec items covered (freshness LIVE/DELAYED/STALE/OFFLINE, evidence badges, sample data, report entry point, mark-stale refresh flow).
- Type consistency: `JourneyFreshness`/`JourneyObservation` names consistent across service/controller/renderer/tests; the renderer overload signature matches the controller call; the fixture JSON keys must match the real `journey` record component names (Task 3 Step 1 note).
- Known plan-level note in Task 1 Step 3: the `freshness(...)` lookup must use `manifest.journeyId()` — the plan's inline sketch has a deliberate error that the note flags; the implementer must apply the fix.

