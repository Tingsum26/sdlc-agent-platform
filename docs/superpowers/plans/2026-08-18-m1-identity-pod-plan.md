# M1 Identity and Pod Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make milestone M1 runnable: fictitious Principal, non-GitHub Scrum Master enrollment, Pod CSV import with member directory, and deterministic Ticket-to-Pod assignment with onboarding badges visible in the Web demo and covered by a browser E2E.

**Architecture:** All state stays in the `fake` Spring profile (in-memory). A new `DirectoryPersonService` tracks every roster member's `OnboardingStatus` (`NOT_ONBOARDED` until their identity is bound). A new `EnrollmentCodeService` issues one-time codes so non-GitHub roles (Scrum Master, BA, QA) can bind an identity. The existing `InternalReadinessController` gains enrollment/bind/members endpoints and upserts directory persons on roster import. The Web UI gains a Pod & Assignment card that parses a bundled fictitious CSV and renders member rows with onboarding badges and a suggested-assignee queue.

**Tech Stack:** Java 17, Spring Boot (existing), React 19 + Vite + Vitest (existing), Playwright (existing).

**Working directory for all commands:** `D:\codex\sdlc-agent-platform\.worktrees\agent-mvp-vertical-slice` (the isolated worktree on branch `agent/mvp-vertical-slice`).

**Verification commands used throughout:**

- Java single test: `mvnw.cmd -q -pl apps/workflow-service -Dtest=EnrollmentCodeServiceTest test` (run from repo root; adjust `-Dtest` per test class)
- Java full: `mvnw.cmd -q verify`
- Node test (web-ui): `pnpm --filter @sdlc/web-ui test`
- Browser E2E: `pnpm e2e:m1` (script added in Task 5)
- Start/stop: `powershell -File scripts/start-demo.ps1` / `powershell -File scripts/stop-demo.ps1`

---

### Task 1: One-time enrollment codes for non-GitHub identities

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/EnrollmentCodeService.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/EnrollmentCodeServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/EnrollmentCodeServiceTest.java`:

```java
package dev.sdlc.workflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EnrollmentCodeServiceTest {

    @Test
    void bindsANonGithubIdentityWithAOneTimeCode() {
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), Clock.systemUTC());

        String code = service.issueCode("EMP-777");
        EnterprisePrincipal principal = service.bind(code, "Fictional BA", "b***@example.invalid");

        assertEquals("PRINCIPAL-EMP-777", principal.principalId());
        assertEquals("EMP-777", principal.employeeId());
        assertEquals(IdentitySource.ADMIN_BINDING, principal.source());
    }

    @Test
    void rejectsReuseOfAnEnrollmentCode() {
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), Clock.systemUTC());

        String code = service.issueCode("EMP-778");
        service.bind(code, "Fictional BA", "b***@example.invalid");

        assertThrows(IllegalArgumentException.class, () -> service.bind(code, "Fictional Other", "o***@example.invalid"));
    }

    @Test
    void rejectsAnExpiredEnrollmentCode() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), clock);

        String code = service.issueCode("EMP-779");
        clock.advance(Duration.ofMinutes(16));

        assertThrows(IllegalArgumentException.class, () -> service.bind(code, "Fictional BA", "b***@example.invalid"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -q -pl apps/workflow-service -Dtest=EnrollmentCodeServiceTest test`
Expected: COMPILATION FAILURE — `cannot find symbol: class EnrollmentCodeService`.

- [ ] **Step 3: Write the implementation**

Create `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/EnrollmentCodeService.java`:

```java
package dev.sdlc.workflow.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Issues one-time enrollment codes so non-GitHub roles (Scrum Master, BA, QA)
 * can bind an enterprise identity without holding a GitHub account.
 */
public final class EnrollmentCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Enrollment> pending = new ConcurrentHashMap<>();
    private final IdentityBindingService bindings;
    private final Clock clock;

    public EnrollmentCodeService(IdentityBindingService bindings, Clock clock) {
        this.bindings = bindings;
        this.clock = clock;
    }

    public String issueCode(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException("employeeId is required");
        }
        String code = "ENROLL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        pending.put(code, new Enrollment(employeeId, clock.instant().plus(CODE_TTL)));
        return code;
    }

    public EnterprisePrincipal bind(String code, String displayLabel, String maskedEmail) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        Enrollment enrollment = pending.get(code);
        if (enrollment == null) {
            throw new IllegalArgumentException("Unknown enrollment code");
        }
        if (clock.instant().isAfter(enrollment.expiresAt())) {
            pending.remove(code);
            throw new IllegalArgumentException("Enrollment code expired");
        }
        pending.remove(code);
        return bindings.bindAdminPrincipal(enrollment.employeeId(), displayLabel, maskedEmail);
    }

    private record Enrollment(String employeeId, Instant expiresAt) {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvnw.cmd -q -pl apps/workflow-service -Dtest=EnrollmentCodeServiceTest test`
Expected: BUILD SUCCESS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/EnrollmentCodeService.java apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/EnrollmentCodeServiceTest.java
git commit -m "feat(m1): add one-time enrollment codes for non-GitHub identities"
```

---

### Task 2: Directory persons with onboarding status

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/OnboardingStatus.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/DirectoryPerson.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/DirectoryPersonService.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/DirectoryPersonServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/DirectoryPersonServiceTest.java`:

```java
package dev.sdlc.workflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class DirectoryPersonServiceTest {

    @Test
    void rosterUpsertKeepsNewMembersNotOnboarded() {
        DirectoryPersonService service = new DirectoryPersonService(Clock.systemUTC());

        service.upsertFromRoster("PRINCIPAL-EMP-201", "EMP-201", "Fictional Developer");

        DirectoryPerson person = service.findByPrincipalId("PRINCIPAL-EMP-201").orElseThrow();
        assertEquals(OnboardingStatus.NOT_ONBOARDED, person.onboardingStatus());
        assertEquals("EMP-201", person.employeeId());
        assertEquals(1, service.listAll().size());
    }

    @Test
    void rosterUpsertPreservesAnAlreadyOnboardedStatus() {
        DirectoryPersonService service = new DirectoryPersonService(Clock.systemUTC());
        service.upsert("PRINCIPAL-EMP-100", "EMP-100", "Fictional Scrum Master", OnboardingStatus.ONBOARDED);

        DirectoryPerson person = service.upsertFromRoster("PRINCIPAL-EMP-100", "EMP-100", "Fictional Scrum Master");

        assertEquals(OnboardingStatus.ONBOARDED, person.onboardingStatus());
    }

    @Test
    void explicitUpsertOverridesStatusForIdentityBinding() {
        DirectoryPersonService service = new DirectoryPersonService(Clock.systemUTC());
        service.upsertFromRoster("PRINCIPAL-EMP-301", "EMP-301", "Fictional iOS Developer");

        DirectoryPerson bound = service.upsert("PRINCIPAL-EMP-301", "EMP-301", "Fictional iOS Developer",
                OnboardingStatus.ONBOARDED);

        assertEquals(OnboardingStatus.ONBOARDED, bound.onboardingStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -q -pl apps/workflow-service -Dtest=DirectoryPersonServiceTest test`
Expected: COMPILATION FAILURE — `cannot find symbol: class DirectoryPersonService`.

- [ ] **Step 3: Write the implementation**

Create `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/OnboardingStatus.java`:

```java
package dev.sdlc.workflow.identity;

public enum OnboardingStatus {
    /** Person is known to the roster but has never bound a workbench identity. */
    NOT_ONBOARDED,
    /** Person has bound a workbench identity (GitHub admin binding or enrollment code). */
    ONBOARDED
}
```

Create `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/DirectoryPerson.java`:

```java
package dev.sdlc.workflow.identity;

import java.time.Instant;

public record DirectoryPerson(
        String principalId,
        String employeeId,
        String displayLabel,
        OnboardingStatus onboardingStatus,
        Instant updatedAt) {
}
```

Create `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/DirectoryPersonService.java`:

```java
package dev.sdlc.workflow.identity;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks every known person independently of whether they installed the
 * workbench. Roster imports create {@code NOT_ONBOARDED} entries; identity
 * binding flips them to {@code ONBOARDED}. A roster import never downgrades
 * an already-onboarded person.
 */
public final class DirectoryPersonService {

    private final ConcurrentMap<String, DirectoryPerson> persons = new ConcurrentHashMap<>();
    private final Clock clock;

    public DirectoryPersonService(Clock clock) {
        this.clock = clock;
    }

    public DirectoryPerson upsert(String principalId, String employeeId, String displayLabel,
            OnboardingStatus onboardingStatus) {
        requireText(principalId, "principalId");
        requireText(employeeId, "employeeId");
        requireText(displayLabel, "displayLabel");
        DirectoryPerson person = new DirectoryPerson(principalId, employeeId, displayLabel, onboardingStatus,
                clock.instant());
        persons.put(principalId, person);
        return person;
    }

    public DirectoryPerson upsertFromRoster(String principalId, String employeeId, String displayLabel) {
        OnboardingStatus existing = persons.containsKey(principalId)
                ? persons.get(principalId).onboardingStatus()
                : OnboardingStatus.NOT_ONBOARDED;
        return upsert(principalId, employeeId, displayLabel, existing);
    }

    public Optional<DirectoryPerson> findByPrincipalId(String principalId) {
        return Optional.ofNullable(persons.get(principalId));
    }

    public List<DirectoryPerson> listAll() {
        List<DirectoryPerson> all = new ArrayList<>(persons.values());
        all.sort(Comparator.comparing(DirectoryPerson::employeeId));
        return all;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvnw.cmd -q -pl apps/workflow-service -Dtest=DirectoryPersonServiceTest test`
Expected: BUILD SUCCESS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/OnboardingStatus.java apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/DirectoryPerson.java apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/DirectoryPersonService.java apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/DirectoryPersonServiceTest.java
git commit -m "feat(m1): add directory persons with onboarding status"
```

---

### Task 3: REST endpoints for enrollment, binding, and roster members

**Files:**
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/InternalReadinessController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java`
- Create: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/InternalReadinessIdentityIT.java`
- Create: `docs/handoff/INTERNAL_TODO.md`

- [ ] **Step 1: Write the failing integration test**

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/InternalReadinessIdentityIT.java`:

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
class InternalReadinessIdentityIT {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void enrollsAndBindsANonGithubIdentity() throws Exception {
        String issued = mvc.perform(post("/api/v1/internal-readiness/identity/enrollment")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"EMP-777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andReturn().getResponse().getContentAsString();
        String code = json.readTree(issued).path("code").asText();

        mvc.perform(post("/api/v1/internal-readiness/identity/bind")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"displayLabel\":\"Fictional BA\","
                                + "\"maskedEmail\":\"b***@example.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value("PRINCIPAL-EMP-777"))
                .andExpect(jsonPath("$.employeeId").value("EMP-777"));
    }

    @Test
    void importMarksRosterMembersNotOnboardedUntilBound() throws Exception {
        mvc.perform(post("/api/v1/internal-readiness/pods/import")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journeyId":"ACCOUNT_OPENING",
                                  "expectedRevision":0,
                                  "memberships":[{
                                    "membershipId":"MEM-DEV-1",
                                    "employeeId":"EMP-201",
                                    "principalId":"PRINCIPAL-EMP-201",
                                    "displayLabel":"Fictional Developer",
                                    "role":"DEVELOPER",
                                    "journeyId":"ACCOUNT_OPENING",
                                    "active":true,
                                    "effectiveFrom":"2026-01-01",
                                    "aliases":[]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));

        mvc.perform(get("/api/v1/internal-readiness/pods/{journeyId}/members", "ACCOUNT_OPENING")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].principalId").value("PRINCIPAL-EMP-201"))
                .andExpect(jsonPath("$[0].onboardingStatus").value("NOT_ONBOARDED"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -q -pl apps/workflow-service -Dtest=InternalReadinessIdentityIT test`
Expected: FAIL — `MockHttpServletResponse ... Status expected:<200> but was:<404>` for `/identity/enrollment` (endpoint does not exist yet).

- [ ] **Step 3: Modify `FakeRuntimeConfiguration`**

Replace the `identityBindingService()` bean and add the two new beans. Open `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java` and change the import block by adding:

```java
import dev.sdlc.workflow.identity.DirectoryPersonService;
import dev.sdlc.workflow.identity.EnrollmentCodeService;
import dev.sdlc.workflow.identity.OnboardingStatus;
```

Replace the existing bean:

```java
    @Bean
    IdentityBindingService identityBindingService() {
        IdentityBindingService service = new IdentityBindingService();
        service.bindAdminPrincipal("EMP-100", "Fictional Scrum Master", "f***@example.invalid");
        return service;
    }
```

with:

```java
    @Bean
    DirectoryPersonService directoryPersonService(Clock clock) {
        return new DirectoryPersonService(clock);
    }

    @Bean
    IdentityBindingService identityBindingService(DirectoryPersonService directory) {
        IdentityBindingService service = new IdentityBindingService();
        dev.sdlc.workflow.identity.EnterprisePrincipal sm =
                service.bindAdminPrincipal("EMP-100", "Fictional Scrum Master", "f***@example.invalid");
        // TODO(INTERNAL): INTERNAL-IDN-002 Seed real admin-principal provisioning instead of the fictional EMP-100.
        directory.upsert(sm.principalId(), sm.employeeId(), sm.displayLabel(), OnboardingStatus.ONBOARDED);
        return service;
    }

    @Bean
    EnrollmentCodeService enrollmentCodeService(IdentityBindingService bindings, Clock clock) {
        return new EnrollmentCodeService(bindings, clock);
    }
```

- [ ] **Step 4: Modify `MongoRuntimeConfiguration`**

Add the same two new beans so the `mongo` profile keeps compiling. Open `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java`, add imports:

```java
import dev.sdlc.workflow.identity.DirectoryPersonService;
import dev.sdlc.workflow.identity.EnrollmentCodeService;
```

and replace:

```java
    @Bean
    IdentityBindingService identityBindingService() { return new IdentityBindingService(); }
```

with:

```java
    @Bean
    DirectoryPersonService directoryPersonService(Clock clock) { return new DirectoryPersonService(clock); }

    @Bean
    IdentityBindingService identityBindingService() { return new IdentityBindingService(); }

    @Bean
    EnrollmentCodeService enrollmentCodeService(IdentityBindingService bindings, Clock clock) {
        return new EnrollmentCodeService(bindings, clock);
    }
```

- [ ] **Step 5: Modify `InternalReadinessController`**

Open `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/InternalReadinessController.java`.

Add imports:

```java
import dev.sdlc.workflow.identity.DirectoryPerson;
import dev.sdlc.workflow.identity.DirectoryPersonService;
import dev.sdlc.workflow.identity.EnrollmentCodeService;
import dev.sdlc.workflow.identity.OnboardingStatus;
```

Add fields and constructor parameters:

```java
    private final EnrollmentCodeService enrollmentCodes;
    private final DirectoryPersonService directory;

    public InternalReadinessController(
            IdentityBindingService identities,
            PodRosterService podService,
            PodRosterRepository podRosters,
            AssignmentService assignmentService,
            TaskAssignmentRepository assignments,
            IntegrationDiagnosticService diagnostics,
            EnrollmentCodeService enrollmentCodes,
            DirectoryPersonService directory) {
        this.identities = identities;
        this.podService = podService;
        this.podRosters = podRosters;
        this.assignmentService = assignmentService;
        this.assignments = assignments;
        this.diagnostics = diagnostics;
        this.enrollmentCodes = enrollmentCodes;
        this.directory = directory;
    }
```

Change `importPod` so roster members enter the directory:

```java
    @PostMapping("/pods/import")
    PodRoster importPod(@Valid @RequestBody ImportPodRequest body, HttpServletRequest request) {
        String principalId = CurrentUser.require(request).actorId();
        PodRoster saved = podService.importRoster(body.journeyId(), body.expectedRevision(), body.memberships(),
                principalId, CorrelationIdFilter.from(request));
        // TODO(INTERNAL): INTERNAL-POD-001 Replace manual roster import with Teambook/HR sync when approved.
        for (PodMembership membership : saved.memberships()) {
            directory.upsertFromRoster(membership.principalId(), membership.employeeId(),
                    membership.displayLabel());
        }
        return saved;
    }
```

Add the three new endpoints after `validatePod`:

```java
    @GetMapping("/pods/{journeyId}/members")
    List<DirectoryPerson> members(@PathVariable String journeyId, HttpServletRequest request) {
        CurrentUser.require(request);
        PodRoster roster = podRosters.find(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Pod roster not found"));
        return roster.memberships().stream()
                .map(PodMembership::principalId)
                .map(directory::findByPrincipalId)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @PostMapping("/identity/enrollment")
    java.util.Map<String, Object> issueEnrollment(@Valid @RequestBody EnrollmentRequest body,
            HttpServletRequest request) {
        CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-IDN-001 Replace demo enrollment-code issuance with the corporate
        // SSO/manual admin binding flow.
        String code = enrollmentCodes.issueCode(body.employeeId());
        return java.util.Map.of("code", code, "expiresInMinutes", 15);
    }

    @PostMapping("/identity/bind")
    EnterprisePrincipal bind(@Valid @RequestBody BindRequest body, HttpServletRequest request) {
        CurrentUser.require(request);
        EnterprisePrincipal principal = enrollmentCodes.bind(body.code(), body.displayLabel(), body.maskedEmail());
        directory.upsert(principal.principalId(), principal.employeeId(), principal.displayLabel(),
                OnboardingStatus.ONBOARDED);
        return principal;
    }
```

Add the request records next to the existing ones:

```java
    public record EnrollmentRequest(@NotBlank String employeeId) {
    }

    public record BindRequest(
            @NotBlank String code,
            @NotBlank String displayLabel,
            String maskedEmail) {
    }
```

- [ ] **Step 6: Create the INTERNAL TODO registry**

Create `docs/handoff/INTERNAL_TODO.md`:

```markdown
# INTERNAL TODO Registry

Every internal-network configuration point in public code carries a
`TODO(INTERNAL): INTERNAL-XXX` marker and MUST be listed here. M8 adds CI
enforcement so an unregistered marker fails the build.

| ID | Component | File | Internal agent action | Evidence required | Rollback |
|---|---|---|---|---|---|
| INTERNAL-IDN-001 | workflow-service | `api/InternalReadinessController.java` | Replace demo enrollment-code issuance with the corporate SSO/manual admin binding flow | Sanitized identity binding test results | Keep the endpoint behind the `fake` profile |
| INTERNAL-IDN-002 | workflow-service | `config/FakeRuntimeConfiguration.java` | Seed real admin-principal provisioning instead of the fictional `EMP-100` | Identity binding log entry | Re-seed the fictitious principal |
| INTERNAL-POD-001 | workflow-service | `api/InternalReadinessController.java` | Replace manual roster import with Teambook/HR sync when approved | Sanitized import report | Keep manual CSV/JSON import active |
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvnw.cmd -q -pl apps/workflow-service -Dtest=InternalReadinessIdentityIT test`
Expected: BUILD SUCCESS (2 tests).

- [ ] **Step 8: Run the full Java suite**

Run: `mvnw.cmd -q verify`
Expected: BUILD SUCCESS with all existing tests plus the new ones.

- [ ] **Step 9: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/api/InternalReadinessController.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java apps/workflow-service/src/test/java/dev/sdlc/workflow/api/InternalReadinessIdentityIT.java docs/handoff/INTERNAL_TODO.md
git commit -m "feat(m1): expose enrollment, binding, and roster member endpoints"
```

---

### Task 4: Web UI Pod & Assignment card with CSV import

**Files:**
- Create: `apps/web-ui/src/podCsv.ts`
- Test: `apps/web-ui/src/podCsv.test.ts`
- Create: `apps/web-ui/public/fixtures/pod-roster.csv`
- Modify: `apps/web-ui/src/App.tsx`

- [ ] **Step 1: Write the failing parser test**

Create `apps/web-ui/src/podCsv.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { parsePodCsv } from "./podCsv";

describe("parsePodCsv", () => {
  it("parses a valid roster", () => {
    const rows = parsePodCsv([
      "employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom",
      "EMP-201,Fictional API Developer,PRINCIPAL-EMP-201,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01",
    ].join("\n"));
    expect(rows).toHaveLength(1);
    expect(rows[0]).toEqual({
      employeeId: "EMP-201",
      displayLabel: "Fictional API Developer",
      principalId: "PRINCIPAL-EMP-201",
      role: "DEVELOPER",
      journeyId: "ACCOUNT_OPENING",
      active: true,
      effectiveFrom: "2026-01-01",
    });
  });

  it("rejects a mismatched header", () => {
    expect(() => parsePodCsv("a,b\n1,2")).toThrow("pod-csv-header-mismatch");
  });

  it("rejects an incomplete row", () => {
    expect(() => parsePodCsv([
      "employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom",
      "EMP-201,,PRINCIPAL-EMP-201,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01",
    ].join("\n"))).toThrow("pod-csv-row-incomplete");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter @sdlc/web-ui test`
Expected: FAIL — `Cannot find module './podCsv'`.

- [ ] **Step 3: Write the parser**

Create `apps/web-ui/src/podCsv.ts`:

```ts
export interface PodCsvRow {
  employeeId: string;
  displayLabel: string;
  principalId: string;
  role: string;
  journeyId: string;
  active: boolean;
  effectiveFrom: string;
}

const EXPECTED_HEADER = "employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom";

export function parsePodCsv(text: string): PodCsvRow[] {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
  const [header, ...body] = lines;
  if (header !== EXPECTED_HEADER) {
    throw new Error("pod-csv-header-mismatch");
  }
  return body.map((line) => {
    const [employeeId, displayLabel, principalId, role, journeyId, active, effectiveFrom] = line.split(",");
    if (!employeeId || !displayLabel || !principalId || !role || !journeyId || !active || !effectiveFrom) {
      throw new Error("pod-csv-row-incomplete");
    }
    return { employeeId, displayLabel, principalId, role, journeyId, active: active === "true", effectiveFrom };
  });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter @sdlc/web-ui test`
Expected: PASS (3 tests).

- [ ] **Step 5: Add the fictitious CSV fixture**

Create `apps/web-ui/public/fixtures/pod-roster.csv`:

```csv
employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom
EMP-100,Fictional Scrum Master,PRINCIPAL-EMP-100,SCRUM_MASTER,ACCOUNT_OPENING,true,2026-01-01
EMP-201,Fictional API Developer,PRINCIPAL-EMP-201,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01
EMP-202,Fictional Web Developer,PRINCIPAL-EMP-202,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01
EMP-301,Fictional iOS Developer,PRINCIPAL-EMP-301,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01
EMP-302,Fictional Android Developer,PRINCIPAL-EMP-302,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01
EMP-401,Fictional QA,PRINCIPAL-EMP-401,QA,ACCOUNT_OPENING,true,2026-01-01
```

- [ ] **Step 6: Extend `App.tsx`**

Open `apps/web-ui/src/App.tsx` and add the import at the top:

```tsx
import { parsePodCsv } from "./podCsv";
```

Add interfaces after the existing ones:

```tsx
interface Member { principalId: string; employeeId: string; displayLabel: string; role: string; onboardingStatus: string }
interface Roster { revision: number }
```

Add state inside `App()` after `const [readinessError, setReadinessError] = useState<string>();`:

```tsx
const [members, setMembers] = useState<Member[]>([]);
const [rosterImporting, setRosterImporting] = useState(false);
const [rosterError, setRosterError] = useState<string>();
const [assigning, setAssigning] = useState(false);
const [queueAssignment, setQueueAssignment] = useState<{ ticketId: string; principalId: string; reason: string }>();
```

Add the two handlers after `runReadiness`:

```tsx
const importRoster = async () => {
  setRosterImporting(true); setRosterError(undefined);
  try {
    const csvResponse = await fetch("/fixtures/pod-roster.csv");
    if (!csvResponse.ok) throw new Error("pod-csv-missing");
    const rows = parsePodCsv(await csvResponse.text());
    const rosterResponse = await fetch("/api/v1/internal-readiness/pods/ACCOUNT_OPENING", { headers: readinessHeaders });
    const expectedRevision = rosterResponse.ok ? ((await rosterResponse.json() as Roster).revision ?? 0) : 0;
    const importResponse = await fetch("/api/v1/internal-readiness/pods/import", {
      method: "POST", headers: readinessHeaders, body: JSON.stringify({
        journeyId: "ACCOUNT_OPENING", expectedRevision,
        memberships: rows.map((row) => ({
          membershipId: `MEM-${row.employeeId}`, employeeId: row.employeeId, principalId: row.principalId,
          displayLabel: row.displayLabel, role: row.role, journeyId: row.journeyId, active: row.active,
          effectiveFrom: row.effectiveFrom, aliases: [],
        })),
      }),
    });
    if (!importResponse.ok) throw new Error("pod-import-failed");
    setMembers(await json<Member[]>("/api/v1/internal-readiness/pods/ACCOUNT_OPENING/members"));
  } catch { setRosterError("pod-roster-import-failed"); }
  finally { setRosterImporting(false); }
};

const assignDeveloper = async () => {
  setAssigning(true);
  try {
    const assigned = await json<{ ticketId: string; principalId: string; reason: string }>(
      "/api/v1/internal-readiness/assignments", {
        method: "POST", body: JSON.stringify({ ticketId: "DEMO-123", journeyId: "ACCOUNT_OPENING", requiredRole: "DEVELOPER" }),
      });
    setQueueAssignment(assigned);
  } finally { setAssigning(false); }
};
```

Add the section UI inside `<main>` after the readiness section, before `</main>`:

```tsx
<section className="sdlc-card sdlc-stack readiness" aria-labelledby="pod-title">
  <div className="section-heading"><div><p className="eyebrow">M1 · Identity &amp; Pod</p><h2 id="pod-title">Pod roster and assignment</h2></div>
    <button type="button" disabled={rosterImporting} aria-busy={rosterImporting} onClick={() => void importRoster()}>
      {rosterImporting ? "Importing roster…" : "Import fictitious Pod roster (CSV)"}
    </button></div>
  {rosterError && <ErrorState title="Pod roster unavailable" correlationId={rosterError} onRetry={() => void importRoster()} />}
  {members.length > 0 && <div className="table-scroll"><table><caption>ACCOUNT_OPENING Pod members</caption>
    <thead><tr><th scope="col">Employee</th><th scope="col">Label</th><th scope="col">Role</th><th scope="col">Onboarding</th></tr></thead>
    <tbody>{members.map((member) => <tr key={member.principalId}>
      <th scope="row">{member.employeeId}</th><td>{member.displayLabel}</td><td>{member.role}</td>
      <td><span aria-hidden="true">◆ </span>{member.onboardingStatus}</td></tr>}</tbody></table></div>}
  <div className="sdlc-actions">
    <button type="button" disabled={assigning || members.length === 0} aria-busy={assigning} onClick={() => void assignDeveloper()}>
      {assigning ? "Assigning…" : "Assign DEMO-123 to first active DEVELOPER"}
    </button>
  </div>
  {queueAssignment && <p role="status">Assigned {queueAssignment.ticketId} · {queueAssignment.principalId} · {queueAssignment.reason}</p>}
  {queueAssignment && members.some((member) =>
    member.principalId === queueAssignment.principalId && member.onboardingStatus === "NOT_ONBOARDED") &&
    <p className="sdlc-muted">ASSIGNEE_NOT_ONBOARDED — this fictitious assignee has not bound a workbench identity yet.</p>}
</section>
```

- [ ] **Step 7: Run parser test and build**

Run: `pnpm --filter @sdlc/web-ui test && pnpm --filter @sdlc/web-ui build`
Expected: PASS then build success.

- [ ] **Step 8: Commit**

```powershell
git add apps/web-ui/src/podCsv.ts apps/web-ui/src/podCsv.test.ts apps/web-ui/public/fixtures/pod-roster.csv apps/web-ui/src/App.tsx
git commit -m "feat(m1): add Pod roster CSV import and assignment UI"
```

---

### Task 5: M1 browser E2E

**Files:**
- Create: `e2e/m1-identity-pod.spec.ts`
- Modify: `package.json` (root)

- [ ] **Step 1: Write the failing E2E**

Create `e2e/m1-identity-pod.spec.ts`:

```ts
import { expect, test } from "@playwright/test";

test("M1: fictitious Pod CSV import lists members and assigns a not-onboarded developer", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Import fictitious Pod roster (CSV)" }).click();

  await expect(page.getByRole("row", { name: /EMP-201/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /EMP-301/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /EMP-100/ })).toContainText("ONBOARDED");
  await expect(page.getByRole("row", { name: /EMP-201/ })).toContainText("NOT_ONBOARDED");

  await page.getByRole("button", { name: "Assign DEMO-123 to first active DEVELOPER" }).click();
  await expect(page.getByText("Assigned DEMO-123 · PRINCIPAL-EMP-201")).toBeVisible();
  await expect(page.getByText(/ASSIGNEE_NOT_ONBOARDED/)).toBeVisible();
});
```

- [ ] **Step 2: Register the script**

Open root `package.json` and change:

```json
    "e2e:public-mvp": "playwright test e2e/public-mvp.spec.ts"
```

to:

```json
    "e2e:public-mvp": "playwright test e2e/public-mvp.spec.ts",
    "e2e:m1": "playwright test e2e/m1-identity-pod.spec.ts"
```

- [ ] **Step 3: Start the stack and run the E2E to verify it fails**

Run: `powershell -File scripts/start-demo.ps1`
Then: `pnpm e2e:m1`
Expected: FAIL — the button "Import fictitious Pod roster (CSV)" is not found (Task 4 not yet in the served build, or assertion mismatch if the section exists). If it already passes because Tasks 1–4 are complete, record PASS and treat the RED step as satisfied at the Task 4 commit boundary.
Then: `powershell -File scripts/stop-demo.ps1`

- [ ] **Step 4: Commit**

```powershell
git add e2e/m1-identity-pod.spec.ts package.json
git commit -m "test(m1): add identity and Pod routing browser E2E"
```

---

### Task 6: Milestone gates and commit

- [ ] **Step 1: Full verification**

Run from the repo root, in order:

```powershell
mvnw.cmd -q verify
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm e2e:m1
pnpm e2e:public-mvp
```

Expected: every command succeeds. Record the passing test counts in the commit body.

- [ ] **Step 2: Lifecycle check**

Run: `powershell -File scripts/start-demo.ps1`
Expected: service health `UP`, web `200`.
Run: `powershell -File scripts/stop-demo.ps1`
Expected: both ports released.

- [ ] **Step 3: Static boundary scan**

Run:

```powershell
Select-String -Path apps\workflow-service\src\main\java\dev\sdlc\workflow\**\*.java -Pattern "password\s*=|secret\s*=\s*\"" -SimpleMatch:$false | Where-Object { $_.Line -notmatch "fictional|example" }
Select-String -Path apps\web-ui\src\*.ts,apps\web-ui\src\*.tsx -Pattern "TODO|TBD" | Where-Object { $_.Line -notmatch "TODO\(INTERNAL\)" }
```

Expected: no output lines.

- [ ] **Step 4: Commit milestone evidence**

```powershell
git add -A
git commit -m "chore(m1): record milestone verification evidence"
```

Then push at the end of the whole run-first phase (or immediately if the owner wants the PR updated):

```powershell
git push
```

---

## Self-review notes

- Spec coverage: M1 spec items — fictitious Principal (existing `EMP-100` seed), non-GitHub SM enrollment (Task 1/3), Pod CSV import with DRY_RUN/APPLY semantics (existing `/pods/validate` + import; CSV parsed client-side in Task 4), deterministic Ticket-to-Pod assignment (existing `AssignmentService`, UI in Task 4), `DirectoryPerson` non-onboarded states (Task 2/3), E2E (Task 5), runnable gates (Task 6). All covered.
- Type consistency: `DirectoryPersonService` methods `upsert`, `upsertFromRoster`, `findByPrincipalId`, `listAll` match the controller usage; `EnrollmentCodeService` methods `issueCode`/`bind` match the test and controller; `parsePodCsv` return shape matches `App.tsx` mapping.
- No placeholders: every step has concrete code or exact commands.
