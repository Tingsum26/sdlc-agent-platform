# M3 Enterprise Adapters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make milestone M3 runnable: Jira projection outbox (draft → human-confirmed publish → retry with `JIRA_ARTIFACT_SYNC_PENDING`/`FAILED`), Jenkins CI status flow into the Ticket View, Splunk structured audit emission with allowlist redaction, and a browser E2E covering "complete a stage → Jira comment draft → confirm publish → simulated Jenkins CI visible".

**Architecture:** New `jiraprojection` domain (record + in-memory repository + service with retry/max-attempts) fed by `JiraProjectionClient` (fake profile: a `FakeJiraProjectionClient` that records comments and can be scripted to fail). New `SplunkAuditPublisher` emits allowlisted structured events through the existing `SplunkDiagnosticAdapter` (transport stays `DeterministicFakeTransport` in the fake profile, with a `TODO(INTERNAL)` for the real HEC endpoint). `EpicController` gains four endpoints (Jira draft create/publish/retry/status + ticket CI record). All fictitious data.

**Tech Stack:** Java 17 Spring Boot (existing), React 19 + Vite (existing), Playwright (existing).

**Working directory:** `D:\codex\sdlc-agent-platform\.worktrees\agent-mvp-vertical-slice`

**Known conventions:** `WorkflowConflictException` → 409; `IllegalArgumentException` → 400; `TODO(INTERNAL)` markers registered in `docs/handoff/INTERNAL_TODO.md`; Playwright suites as separate invocations; do not pipe start/stop scripts.

---

### Task 1: Jira projection outbox domain and service

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/JiraProjection.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/JiraProjectionRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/InMemoryJiraProjectionRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/JiraProjectionService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/FakeJiraProjectionClient.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/jiraprojection/JiraProjectionServiceTest.java`

- [ ] **Step 1: Write the failing test**

`apps/workflow-service/src/test/java/dev/sdlc/workflow/jiraprojection/JiraProjectionServiceTest.java`:

```java
package dev.sdlc.workflow.jiraprojection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sdlc.workflow.artifact.JiraProjectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JiraProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private record Fixture(JiraProjectionService service, FakeJiraProjectionClient client) {
    }

    private Fixture fixture() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FakeJiraProjectionClient client = new FakeJiraProjectionClient();
        JiraProjectionService service = new JiraProjectionService(new InMemoryJiraProjectionRepository(),
                client, clock);
        return new Fixture(service, client);
    }

    @Test
    void enqueueStartsPendingAndFlushPublishes() {
        Fixture fixture = fixture();
        JiraProjection draft = fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");
        assertEquals(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, draft.status());
        assertEquals(0, draft.attempts());

        JiraProjection published = fixture.service().flushPending("EMP-100", "corr-2").get(0);
        assertEquals(JiraProjectionStatus.PUBLISHED, published.status());
        assertEquals(1, published.attempts());
        assertEquals(1, fixture.client().published().size());
    }

    @Test
    void enqueueIsIdempotentPerTicketAndMilestone() {
        Fixture fixture = fixture();
        fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");
        JiraProjection again = fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Different summary", "EMP-100", "corr-2");
        assertEquals("Requirement approved", again.summary());
        assertEquals(1, fixture.service().listAll().size());
    }

    @Test
    void failingClientStaysPendingAndFailsAfterMaxAttempts() {
        Fixture fixture = fixture();
        fixture.client().failNext();
        fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");

        JiraProjection afterFirst = fixture.service().flushPending("EMP-100", "corr-2").get(0);
        assertEquals(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, afterFirst.status());
        assertEquals(1, afterFirst.attempts());

        fixture.client().failNext();
        fixture.client().failNext();
        fixture.service().flushPending("EMP-100", "corr-3");
        JiraProjection afterThird = fixture.service().flushPending("EMP-100", "corr-4").get(0);
        assertEquals(JiraProjectionStatus.JIRA_ARTIFACT_SYNC_FAILED, afterThird.status());
        assertEquals(3, afterThird.attempts());
    }

    @Test
    void flushSkipsPublishedProjections() {
        Fixture fixture = fixture();
        fixture.service().enqueue("DEMO-123", "REQ-APPROVED", "Requirement approved", "EMP-100", "corr-1");
        fixture.service().flushPending("EMP-100", "corr-2");
        assertEquals(0, fixture.service().flushPending("EMP-100", "corr-3").size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JiraProjectionServiceTest test`
Expected: COMPILATION FAILURE — missing `JiraProjection`, `JiraProjectionService`, etc.

- [ ] **Step 3: Implement the domain**

`JiraProjection.java`:

```java
package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.JiraProjectionStatus;
import java.time.Instant;
import java.util.Objects;

public record JiraProjection(
        String projectionId,
        String ticketId,
        String milestoneId,
        String summary,
        JiraProjectionStatus status,
        int attempts,
        Instant createdAt,
        Instant updatedAt) {

    public JiraProjection {
        Objects.requireNonNull(projectionId, "projectionId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(milestoneId, "milestoneId");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    JiraProjection withStatus(JiraProjectionStatus next, int nextAttempts, Instant now) {
        return new JiraProjection(projectionId, ticketId, milestoneId, summary, next, nextAttempts, createdAt, now);
    }
}
```

`JiraProjectionRepository.java`:

```java
package dev.sdlc.workflow.jiraprojection;

import java.util.List;
import java.util.Optional;

public interface JiraProjectionRepository {
    Optional<JiraProjection> findById(String projectionId);
    JiraProjection save(JiraProjection projection);
    List<JiraProjection> findAll();
}
```

`InMemoryJiraProjectionRepository.java`:

```java
package dev.sdlc.workflow.jiraprojection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryJiraProjectionRepository implements JiraProjectionRepository {
    private final ConcurrentMap<String, JiraProjection> projections = new ConcurrentHashMap<>();

    @Override
    public Optional<JiraProjection> findById(String projectionId) {
        return Optional.ofNullable(projections.get(projectionId));
    }

    @Override
    public JiraProjection save(JiraProjection projection) {
        projections.put(projection.projectionId(), projection);
        return projection;
    }

    @Override
    public List<JiraProjection> findAll() {
        return new ArrayList<>(projections.values());
    }
}
```

`FakeJiraProjectionClient.java` (implements the existing `JiraProjectionClient`):

```java
package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.JiraProjectionClient;
import java.util.ArrayList;
import java.util.List;

public final class FakeJiraProjectionClient implements JiraProjectionClient {
    private final List<String> published = new ArrayList<>();
    private boolean failNext;

    public void failNext() {
        this.failNext = true;
    }

    @Override
    public synchronized void publish(String ticketId, String summary, String html) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("Fictional Jira outage");
        }
        published.add(ticketId + "|" + summary + "|" + html);
    }

    public synchronized List<String> published() {
        return List.copyOf(published);
    }
}
```

`JiraProjectionService.java`:

```java
package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.JiraProjectionClient;
import dev.sdlc.workflow.artifact.JiraProjectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JiraProjectionService {

    public static final int MAX_ATTEMPTS = 3;

    private final JiraProjectionRepository projections;
    private final JiraProjectionClient client;
    private final Clock clock;

    public JiraProjectionService(JiraProjectionRepository projections, JiraProjectionClient client, Clock clock) {
        this.projections = projections;
        this.client = client;
        this.clock = clock;
    }

    public synchronized JiraProjection enqueue(String ticketId, String milestoneId, String summary,
            String actorId, String correlationId) {
        if (ticketId == null || ticketId.isBlank()) throw new IllegalArgumentException("ticketId is required");
        if (milestoneId == null || milestoneId.isBlank()) throw new IllegalArgumentException("milestoneId is required");
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary is required");
        JiraProjection existing = projections.findAll().stream()
                .filter(item -> item.ticketId().equals(ticketId) && item.milestoneId().equals(milestoneId))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        Instant now = clock.instant();
        String projectionId = "JIRA-PROJ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        JiraProjection draft = new JiraProjection(projectionId, ticketId, milestoneId, summary,
                JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING, 0, now, now);
        projections.save(draft);
        return draft;
    }

    public synchronized List<JiraProjection> flushPending(String actorId, String correlationId) {
        List<JiraProjection> changed = new ArrayList<>();
        for (JiraProjection projection : projections.findAll()) {
            if (projection.status() != JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING) continue;
            JiraProjection updated = attempt(projection);
            projections.save(updated);
            changed.add(updated);
        }
        return changed;
    }

    public JiraProjection get(String projectionId) {
        return projections.findById(projectionId)
                .orElseThrow(() -> new IllegalArgumentException("Projection not found: " + projectionId));
    }

    public List<JiraProjection> listAll() {
        return projections.findAll();
    }

    private JiraProjection attempt(JiraProjection projection) {
        try {
            client.publish(projection.ticketId(), projection.summary(), "");
            return projection.withStatus(JiraProjectionStatus.PUBLISHED,
                    projection.attempts() + 1, clock.instant());
        } catch (RuntimeException exception) {
            int attempts = projection.attempts() + 1;
            JiraProjectionStatus status = attempts >= MAX_ATTEMPTS
                    ? JiraProjectionStatus.JIRA_ARTIFACT_SYNC_FAILED
                    : JiraProjectionStatus.JIRA_ARTIFACT_SYNC_PENDING;
            return projection.withStatus(status, attempts, clock.instant());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JiraProjectionServiceTest test`
Expected: BUILD SUCCESS (4 tests).

- [ ] **Step 5: Run the full Java suite**

Run: `.\mvnw.cmd -q verify`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection apps/workflow-service/src/test/java/dev/sdlc/workflow/jiraprojection
git commit -m "feat(m3): add the Jira projection outbox with retry and max attempts"
```

---

### Task 2: REST endpoints, config wiring, registry, IT

**Files:**
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java`
- Modify: `docs/handoff/INTERNAL_TODO.md`
- Create: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JiraProjectionIT.java`

- [ ] **Step 1: Write the failing IT**

`apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JiraProjectionIT.java`:

```java
package dev.sdlc.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class JiraProjectionIT {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void draftsAndPublishesAJiraCommentForACompletedStage() throws Exception {
        String created = mvc.perform(post("/api/v1/tasks/{taskId}/jira-drafts", "TASK-ANY")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"milestoneId\":\"REQ-APPROVED\",\"summary\":\"Requirement approved\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("JIRA_ARTIFACT_SYNC_PENDING"))
                .andReturn().getResponse().getContentAsString();
        String projectionId = json.readTree(created).path("projectionId").asText();

        mvc.perform(post("/api/v1/jira-drafts/{id}/publish", projectionId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.attempts").value(1));

        mvc.perform(get("/api/v1/jira-drafts/{id}", projectionId)
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestoneId").value("REQ-APPROVED"));
    }

    @Test
    void recordsJenkinsCiAndAdvancesTheTicket() throws Exception {
        mvc.perform(post("/api/v1/epics")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epicId\":\"EPIC-M3-1\",\"title\":\"Fictional M3 epic\",\"journeyId\":\"ACCOUNT_OPENING\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/epics/{id}/activate", "EPIC-M3-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M3-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"M3-API-1\",\"channel\":\"API\"}"))
                .andExpect(status().isCreated());

        long version = 0;
        for (String next : new String[] {"IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN"}) {
            String body = mvc.perform(post("/api/v1/tickets/{id}/advance", "M3-API-1")
                            .header("X-Demo-User", "PRINCIPAL-EMP-100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + version + ",\"target\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            version = json.readTree(body).path("version").asLong();
        }

        mvc.perform(post("/api/v1/tickets/{id}/ci", "M3-API-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryAlias\":\"REPO_A\",\"revision\":\"0123456789abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CI_PASSED"))
                .andExpect(jsonPath("$.state").value("PASSED"));
    }
}
```

Note: the first test drafts a projection for a non-existent task id — the draft endpoint must NOT require a real task (the projection is keyed by ticket id, not task state). If the controller implementation instead validates the task, change the endpoint semantics: `POST /api/v1/jira-drafts` with `{ticketId, milestoneId, summary}` (no task lookup). Update the IT accordingly: post `{"ticketId":"DEMO-123","milestoneId":"REQ-APPROVED","summary":"Requirement approved"}` to `/api/v1/jira-drafts` (expect 201). Choose whichever the plan body below states and keep test/implementation consistent.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JiraProjectionIT test`
Expected: FAIL — 404/500 on the new endpoints.

- [ ] **Step 3: Implement controller additions**

In `EpicController.java`, inject `JiraProjectionService jiraProjections` (add constructor param; update the configs in Step 4) and add these endpoints:

```java
    @PostMapping("/jira-drafts")
    ResponseEntity<JiraProjection> createJiraDraft(@Valid @RequestBody JiraDraftRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-JIRA-001 Route the projection outbox to the real Jira comment API.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jiraProjections.enqueue(body.ticketId(), body.milestoneId(), body.summary(),
                        user.actorId(), CorrelationIdFilter.from(request)));
    }

    @PostMapping("/jira-drafts/{projectionId}/publish")
    JiraProjection publishJiraDraft(@PathVariable String projectionId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        JiraProjection draft = jiraProjections.get(projectionId);
        if (draft.version0checkNeeded()) {
            // no-op placeholder removed below
        }
        return jiraProjections.flushPending(user.actorId(), CorrelationIdFilter.from(request)).stream()
                .filter(item -> item.projectionId().equals(projectionId))
                .findFirst()
                .orElseThrow(() -> new dev.sdlc.workflow.conflict.WorkflowConflictException(
                        "Projection is not pending"));
    }

    @PostMapping("/jira-drafts/retry")
    List<JiraProjection> retryJiraDrafts(HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return jiraProjections.flushPending(user.actorId(), CorrelationIdFilter.from(request));
    }

    @GetMapping("/jira-drafts/{projectionId}")
    JiraProjection jiraDraft(@PathVariable String projectionId, HttpServletRequest request) {
        CurrentUser.require(request);
        return jiraProjections.get(projectionId);
    }

    @PostMapping("/tickets/{ticketId}/ci")
    java.util.Map<String, Object> recordCi(@PathVariable String ticketId, @Valid @RequestBody CiRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-CI-001 Route CI status to the real Jenkins adapter; the fake profile uses the mock PASSED adapter.
        CiStatus status = ciStatusAdapter.getStatus(body.repositoryAlias(), body.revision());
        TicketWorkflow ticket = tickets.ticket(ticketId);
        TicketWorkflow advanced = tickets.transition(ticketId, ticket.version(),
                status.state() == CiState.PASSED ? TicketDeliveryStatus.CI_PASSED : TicketDeliveryStatus.BLOCKED,
                user.actorId(), CorrelationIdFilter.from(request));
        return java.util.Map.of("ticket", advanced, "status", status.state().name(), "state", status.state().name(),
                "detailsUrl", status.detailsUrl());
    }

    public record JiraDraftRequest(@NotBlank String ticketId, @NotBlank String milestoneId,
            @NotBlank String summary) {
    }

    public record CiRequest(@NotBlank String repositoryAlias, @NotBlank String revision) {
    }
```

Replace the placeholder `version0checkNeeded` block with a simple guard: read the draft, and if its status is not `JIRA_ARTIFACT_SYNC_PENDING`, throw `WorkflowConflictException("Projection is not pending")`; then flush and return the updated projection (the filter above already returns only the matching pending one). Also inject `CiStatusAdapter ciStatusAdapter` (constructor param; configs updated below). Add imports: `dev.sdlc.workflow.jiraprojection.JiraProjection`, `JiraProjectionService`, `dev.sdlc.workflow.integration.CiStatusAdapter`, `dev.sdlc.workflow.integration.CiState`, `dev.sdlc.workflow.integration.CiStatus`.

Note on the IT: the controller above defines `POST /api/v1/jira-drafts` (ticketId-based, no task lookup) — update the IT's first request to `post("/api/v1/jira-drafts")` with the ticketId body as described in Step 1's note, expecting 201.

- [ ] **Step 4: Wire beans in both configs**

In `FakeRuntimeConfiguration.java` add imports and beans:

```java
import dev.sdlc.workflow.jiraprojection.FakeJiraProjectionClient;
import dev.sdlc.workflow.jiraprojection.InMemoryJiraProjectionRepository;
import dev.sdlc.workflow.jiraprojection.JiraProjectionClient;
import dev.sdlc.workflow.jiraprojection.JiraProjectionRepository;
import dev.sdlc.workflow.jiraprojection.JiraProjectionService;
```

```java
    @Bean
    JiraProjectionRepository jiraProjectionRepository() {
        return new InMemoryJiraProjectionRepository();
    }

    @Bean
    JiraProjectionClient jiraProjectionClient() {
        // TODO(INTERNAL): INTERNAL-JIRA-001 Replace the fake projection client with the real Jira comment API.
        return new FakeJiraProjectionClient();
    }

    @Bean
    JiraProjectionService jiraProjectionService(JiraProjectionRepository projections,
            JiraProjectionClient client, Clock clock) {
        return new JiraProjectionService(projections, client, clock);
    }

    @Bean
    CiStatusAdapter ciStatusAdapter() {
        // TODO(INTERNAL): INTERNAL-CI-001 Replace the mock CI adapter with the real Jenkins adapter.
        return new dev.sdlc.workflow.integration.MockCiStatusAdapter();
    }
```

Copy the SAME four beans into `MongoRuntimeConfiguration.java` (same imports; in-memory in both profiles for M3).

- [ ] **Step 5: Update the registry**

Append to `docs/handoff/INTERNAL_TODO.md`:

```markdown
| INTERNAL-JIRA-001 | workflow-service | `config/*RuntimeConfiguration.java`, `api/EpicController.java` | Route the Jira projection outbox to the real Jira comment API with credentials | Sanitized Jira comment publish log | Revert to the fake projection client |
| INTERNAL-CI-001 | workflow-service | `config/*RuntimeConfiguration.java`, `api/EpicController.java` | Route CI status to the real Jenkins adapter | Sanitized CI status log | Revert to the mock CI adapter |
| INTERNAL-SPLUNK-001 | workflow-service | `splunk/SplunkAuditPublisher.java` (Task 3) | Point the Splunk audit publisher at the real HEC endpoint | Sanitized HEC event log | Revert to the fake transport |
```

- [ ] **Step 6: Run tests**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=JiraProjectionIT test`
Expected: BUILD SUCCESS. Then `.\mvnw.cmd -q verify` — BUILD SUCCESS.

- [ ] **Step 7: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JiraProjectionIT.java docs/handoff/INTERNAL_TODO.md
git commit -m "feat(m3): add Jira projection and Jenkins CI endpoints with wiring"
```

---

### Task 3: Splunk structured audit emission with redaction

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/splunk/SplunkAuditPublisher.java`
- Create: `apps/workflow-service/src/test/java/dev/sdlc/workflow/splunk/SplunkAuditPublisherTest.java`

- [ ] **Step 1: Write the failing test**

`apps/workflow-service/src/test/java/dev/sdlc/workflow/splunk/SplunkAuditPublisherTest.java`:

```java
package dev.sdlc.workflow.splunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.enterprise.DeterministicFakeTransport;
import dev.sdlc.workflow.enterprise.EnterpriseProvider;
import dev.sdlc.workflow.enterprise.EnterpriseRequest;
import dev.sdlc.workflow.enterprise.EnterpriseResponse;
import dev.sdlc.workflow.integration.SplunkDiagnosticAdapter;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplunkAuditPublisherTest {

    private record Fixture(SplunkAuditPublisher publisher, DeterministicFakeTransport transport) {
    }

    private Fixture fixture() {
        DeterministicFakeTransport transport = new DeterministicFakeTransport();
        transport.script(EnterpriseProvider.SPLUNK, "audit-event",
                new EnterpriseResponse(200, "{}", Map.of()));
        SplunkAuditPublisher publisher = new SplunkAuditPublisher(
                new SplunkDiagnosticAdapter(transport, new ObjectMapper(), Clock.systemUTC()));
        return new Fixture(publisher, transport);
    }

    @Test
    void emitsOnlyAllowlistedFieldsAndRedactsSecrets() {
        Fixture fixture = fixture();
        fixture.publisher().jiraProjection("DEMO-123", "REQ-APPROVED", "PUBLISHED",
                "corr-1", "password=secret-value should be dropped");

        assertEquals(1, fixture.transport().ledger().size());
        EnterpriseRequest request = fixture.transport().ledger().get(0);
        assertEquals(EnterpriseProvider.SPLUNK, request.provider());
        assertTrue(request.body().contains("\"event\""));
        assertTrue(!request.body().contains("secret-value"));
        assertTrue(!request.body().contains("password"));
    }

    @Test
    void ciEventCarriesTicketAndState() {
        Fixture fixture = fixture();
        fixture.publisher().ciStatus("M3-API-1", "REPO_A", "PASSED", "corr-2");

        assertEquals(1, fixture.transport().ledger().size());
        EnterpriseRequest request = fixture.transport().ledger().get(0);
        assertTrue(request.body().contains("M3-API-1"));
        assertTrue(request.body().contains("PASSED"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=SplunkAuditPublisherTest test`
Expected: COMPILATION FAILURE — missing `SplunkAuditPublisher`.

- [ ] **Step 3: Implement the publisher**

`apps/workflow-service/src/main/java/dev/sdlc/workflow/splunk/SplunkAuditPublisher.java`:

```java
package dev.sdlc.workflow.splunk;

import dev.sdlc.workflow.integration.SplunkDiagnosticAdapter;
import java.util.List;
import java.util.Map;

/**
 * Emits allowlisted structured audit events to Splunk through the diagnostic
 * adapter. Only the fields in the adapter's allowlist survive; everything
 * else is dropped by the sanitizer, so callers may pass rich detail safely.
 */
public final class SplunkAuditPublisher {

    private final SplunkDiagnosticAdapter splunk;

    public SplunkAuditPublisher(SplunkDiagnosticAdapter splunk) {
        this.splunk = splunk;
    }

    public void jiraProjection(String ticketId, String milestoneId, String status, String correlationId,
            String detail) {
        splunk.publish(List.of(Map.of(
                "component", "workflow-service",
                "event", "jira_projection",
                "correlationId", correlationId,
                "taskId", ticketId,
                "status", status,
                "detail", milestoneId + " " + detail)));
    }

    public void ciStatus(String ticketId, String repositoryAlias, String state, String correlationId) {
        splunk.publish(List.of(Map.of(
                "component", "workflow-service",
                "event", "ci_status",
                "correlationId", correlationId,
                "taskId", ticketId,
                "status", state,
                "detail", repositoryAlias)));
    }
}
```

Wire the publisher usage: in `EpicController.recordCi`, after the transition, call `splunkAudit.ciStatus(...)`; in `JiraProjectionService` there is no access to the publisher — instead call it from `EpicController.publishJiraDraft`/`retryJiraDrafts` after flushing (for each changed projection). Add the `SplunkAuditPublisher` bean to both configs (construct it from `SplunkDiagnosticAdapter` — which needs `EnterpriseTransport`, `ObjectMapper`, `Clock`; in the fake profile, `EnterpriseTransport` bean must exist — check `FakeRuntimeConfiguration`/`IntegrationDiagnosticService` wiring: the diagnostics service currently constructs its own transport? READ `IntegrationDiagnosticService` and wire consistently; if no `EnterpriseTransport` bean exists, create one using `DeterministicFakeTransport` in the fake profile with a `TODO(INTERNAL)` marker and a `JavaHttpEnterpriseTransport` in the mongo profile).

- [ ] **Step 4: Run tests**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=SplunkAuditPublisherTest test`
Expected: BUILD SUCCESS (2 tests). Then `.\mvnw.cmd -q verify` — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/splunk apps/workflow-service/src/test/java/dev/sdlc/workflow/splunk apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java
git commit -m "feat(m3): emit allowlisted Splunk audit events for projections and CI"
```

---

### Task 4: Web UI M3 enterprise section

**Files:**
- Modify: `apps/web-ui/src/App.tsx`
- Test: existing `apps/web-ui/src/App.test.tsx` must stay green

- [ ] **Step 1: Add state and handlers**

Add interfaces after the existing ones:

```tsx
interface JiraDraftState { projectionId: string; ticketId: string; milestoneId: string; summary: string; status: string; attempts: number }
interface CiStateLine { ticketId: string; status: string; detailsUrl: string }
```

Add state inside `App()`:

```tsx
const [jiraDraft, setJiraDraft] = useState<JiraDraftState>();
const [ciLine, setCiLine] = useState<CiStateLine>();
```

Add handlers after the existing M2 handlers:

```tsx
const draftJiraComment = async () => {
  setM2Busy("jira");
  try {
    setJiraDraft(await m2Api<JiraDraftState>("/api/v1/jira-drafts", {
      method: "POST", body: JSON.stringify({ ticketId: "DEMO-123", milestoneId: "REQ-APPROVED", summary: "Requirement approved" }),
    }));
  } catch { setDependencyError("jira-draft-failed"); } finally { setM2Busy(undefined); }
};

const publishJiraComment = async () => {
  if (!jiraDraft) return;
  setM2Busy("jira");
  try {
    setJiraDraft(await m2Api<JiraDraftState>(`/api/v1/jira-drafts/${jiraDraft.projectionId}/publish`, {
      method: "POST", body: JSON.stringify({ expectedVersion: 0 }),
    }));
  } catch { setDependencyError("jira-publish-failed"); } finally { setM2Busy(undefined); }
};

const retryJiraComments = async () => {
  setM2Busy("jira");
  try {
    const updated = await m2Api<JiraDraftState[]>("/api/v1/jira-drafts/retry", { method: "POST", body: "{}" });
    const mine = updated.find((item) => item.projectionId === jiraDraft?.projectionId);
    if (mine) setJiraDraft(mine);
  } catch { setDependencyError("jira-retry-failed"); } finally { setM2Busy(undefined); }
};

const recordJenkinsCi = async () => {
  setM2Busy("ci");
  try {
    const result = await m2Api<{ ticket: TicketState; status: string; detailsUrl: string }>("/api/v1/tickets/M2-API-1/ci", {
      method: "POST", body: JSON.stringify({ repositoryAlias: "REPO_A", revision: "0123456789abcdef" }),
    });
    setCiLine({ ticketId: result.ticket.ticketId, status: result.ticket.status, detailsUrl: result.detailsUrl });
    await refreshTickets(epic!.epicId);
  } catch { setDependencyError("ci-record-failed"); } finally { setM2Busy(undefined); }
};
```

Also add a handler to advance M2-API-1 to PR_OPEN (mirrors `advanceWebTicket` but for M2-API-1 with the 4-step path `["IN_ANALYSIS","WAITING_FOR_APPROVAL","IN_DEVELOPMENT","PR_OPEN"]`):

```tsx
const advanceApiTicketToPr = async () => {
  const ticket = tickets.find((item) => item.ticketId === "M2-API-1");
  if (!ticket) return;
  setM2Busy("advance-api");
  const path: Array<"IN_ANALYSIS" | "WAITING_FOR_APPROVAL" | "IN_DEVELOPMENT" | "PR_OPEN"> =
    ["IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN"];
  try {
    let current = ticket;
    for (const target of path) {
      current = await m2Api<TicketState>(`/api/v1/tickets/${current.ticketId}/advance`, {
        method: "POST", body: JSON.stringify({ expectedVersion: current.version, target }),
      });
    }
    await refreshTickets(epic!.epicId);
  } catch { setDependencyError("advance-api-failed"); } finally { setM2Busy(undefined); }
};
```

- [ ] **Step 2: Add the M3 section JSX (after the M2 section)**

```tsx
<section className="sdlc-card sdlc-stack readiness" aria-labelledby="m3-title">
  <div className="section-heading"><div><p className="eyebrow">M3 · Enterprise adapters</p><h2 id="m3-title">Jira projection and Jenkins CI</h2></div></div>
  <div className="sdlc-actions">
    <button type="button" disabled={Boolean(m2Busy) || Boolean(jiraDraft)} aria-busy={Boolean(m2Busy)} onClick={() => void draftJiraComment()}>Draft Jira comment for DEMO-123</button>
    <button type="button" disabled={Boolean(m2Busy) || !jiraDraft || jiraDraft.status !== "JIRA_ARTIFACT_SYNC_PENDING"} aria-busy={Boolean(m2Busy)} onClick={() => void publishJiraComment()}>Confirm publish Jira comment</button>
    <button type="button" disabled={Boolean(m2Busy) || !jiraDraft} aria-busy={Boolean(m2Busy)} onClick={() => void retryJiraComments()}>Retry pending Jira comments</button>
    <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-API-1" && item.status === "PLANNED")} aria-busy={Boolean(m2Busy)} onClick={() => void advanceApiTicketToPr()}>Advance M2-API-1 to PR_OPEN</button>
    <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-API-1" && item.status === "PR_OPEN")} aria-busy={Boolean(m2Busy)} onClick={() => void recordJenkinsCi()}>Record Jenkins CI for M2-API-1</button>
  </div>
  {jiraDraft && <p role="status">{jiraDraft.projectionId} · {jiraDraft.milestoneId} · {jiraDraft.status} · attempts {jiraDraft.attempts}</p>}
  {ciLine && <p role="status">{ciLine.ticketId} · {ciLine.status} · {ciLine.detailsUrl}</p>}
</section>
```

- [ ] **Step 3: Verify tests and build**

Run: `pnpm --filter @sdlc/web-ui test && pnpm --filter @sdlc/web-ui build`
Expected: PASS then build success.

- [ ] **Step 4: Commit**

```powershell
git add apps/web-ui/src/App.tsx
git commit -m "feat(m3): add the Jira projection and Jenkins CI panel"
```

---

### Task 5: M3 browser E2E, gates, evidence

**Files:**
- Create: `e2e/m3-enterprise-adapters.spec.ts`
- Modify: `package.json` (root)
- Create: `docs/verification/m3-milestone-2026-08-18.md`

- [ ] **Step 1: Write the E2E**

`e2e/m3-enterprise-adapters.spec.ts`:

```ts
import { expect, test } from "@playwright/test";

test("M3: Jira comment draft-publish and simulated Jenkins CI reach the ticket view", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Create EPIC-M2-1" }).click();
  await page.getByRole("button", { name: "Activate epic" }).click();
  await page.getByRole("button", { name: "Attach four channel tickets" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toBeVisible();

  await page.getByRole("button", { name: "Draft Jira comment for DEMO-123" }).click();
  await expect(page.getByText(/JIRA-PROJ-.*· REQ-APPROVED · JIRA_ARTIFACT_SYNC_PENDING/)).toBeVisible();
  await page.getByRole("button", { name: "Confirm publish Jira comment" }).click();
  await expect(page.getByText(/JIRA-PROJ-.*· REQ-APPROVED · PUBLISHED · attempts 1/)).toBeVisible();

  await page.getByRole("button", { name: "Advance M2-API-1 to PR_OPEN" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toContainText("PR_OPEN");
  await page.getByRole("button", { name: "Record Jenkins CI for M2-API-1" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toContainText("CI_PASSED");
  await expect(page.getByText(/M2-API-1 · CI_PASSED · https:\/\/example.invalid/)).toBeVisible();
});
```

- [ ] **Step 2: Register the script**

In root `package.json` add after `"e2e:m2"`:

```json
    "e2e:m3": "playwright test e2e/m3-enterprise-adapters.spec.ts"
```

- [ ] **Step 3: Run the E2E until green**

Run: `pnpm e2e:m3` (own invocation) — expect 1 passed. Then `pnpm e2e:m1`, `pnpm e2e:m2`, `pnpm e2e:public-mvp` separately — expect 1 passed each.

- [ ] **Step 4: Full gates**

```powershell
.\mvnw.cmd -q verify
pnpm install --frozen-lockfile
pnpm test
pnpm build
```
Then lifecycle unpiped: `powershell -File scripts/start-demo.ps1` → "Public demo ready"; `powershell -File scripts/stop-demo.ps1` → ports released. Then the two static scans from the M2 plan (TODO/TBD excluding `TODO(INTERNAL)`; credentials) — expect no output.

- [ ] **Step 5: Evidence doc + commit**

Create `docs/verification/m3-milestone-2026-08-18.md` mirroring the M2 doc: gate table, the M3 commit list (`git log --oneline 23a614f..HEAD` minus the evidence commit itself), new `TODO(INTERNAL)` IDs (`INTERNAL-JIRA-001`, `INTERNAL-CI-001`, `INTERNAL-SPLUNK-001`), quirks. Then:

```powershell
git add e2e/m3-enterprise-adapters.spec.ts package.json docs/verification/m3-milestone-2026-08-18.md
git commit -m "test(m3): add enterprise adapter E2E and milestone evidence"
```

---

## Self-review notes

- Spec coverage: M3 spec items — projection outbox with PENDING/retry/FAILED (Task 1), draft→confirm publish (Tasks 2/4/5), Jenkins CI status flow into Ticket View (Tasks 2/4/5), Splunk structured emission + redaction tests (Task 3), E2E (Task 5), runnable gates (Task 5). Registry gains 3 IDs.
- Type consistency: `JiraProjection.withStatus` signature used in the service; controller endpoints match the UI's `m2Api` calls; `CiStatus.state()`/`detailsUrl()` accessors exist in the integration package (verify at implementation time; adapt minimally if named differently).
- No placeholders: the only flagged ambiguity is the draft-endpoint task-vs-ticket choice, resolved explicitly in Task 2 Step 1's note.

