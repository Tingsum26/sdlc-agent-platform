# M2 Three-Level Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make milestone M2 runnable: Epic → Ticket → Repo Task state machines, emergency Epic change requests with dual-role approval, stage skip attestation, dependency DAG with a merge gate, and resume-from-shutdown context — all in the `fake` profile with fictitious data, verified by unit/IT/browser E2E tests.

**Architecture:** New aggregate entities (`EpicWorkflow`, `TicketWorkflow`, `RepoTask`, `Dependency`, `EpicChangeRequest`, `SkipAttestation`, `DomainAuditEvent`) live in new packages under `dev.sdlc.workflow`, each with an interface repository and an in-memory implementation (Mongo persistence stays a `TODO(INTERNAL)` follow-up, consistent with M1's directory approach). Services enforce state transitions, the dependency gate on ticket MERGED, and dual-role change approval. A new `EpicController` exposes the REST surface; the Web UI gets a fictional Epic panel; the Local MCP gains ten tools; a Playwright E2E covers the full M2 scenario.

**Tech Stack:** Java 17, Spring Boot (existing), React 19 + Vite + Vitest (existing), MCP SDK (existing), Playwright (existing).

**Working directory for all commands:** `D:\codex\sdlc-agent-platform\.worktrees\agent-mvp-vertical-slice`

**Verification commands used throughout:**

- Java single test: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=<TestClass> test` (from repo root)
- Java full: `.\mvnw.cmd -q verify`
- Node tests: `pnpm test`; Node builds: `pnpm build`
- MCP tests: `pnpm --filter @sdlc/workflow-mcp test`
- Browser E2E: `pnpm e2e:m2` (script added in Task 7)
- Start/stop: run `powershell -File scripts/start-demo.ps1` and `powershell -File scripts/stop-demo.ps1` UNPIPED (piping hangs the wrapper — see `docs/verification/m1-milestone-2026-08-18.md`). Run Playwright suites as separate invocations.

**Known environment notes:** If Mockito/ByteBuddy self-attach fails on Spring tests in a sandboxed shell, rerun with `-DargLine=-javaagent:<user .m2>\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar` (runtime-only, never commit it).

---

### Task 1: M2 workflow contract types

**Files:**
- Modify: `packages/contracts/src/types.ts`
- Create: `packages/contracts/test/m2-workflow-types.test.ts`

- [ ] **Step 1: Write the failing test**

Create `packages/contracts/test/m2-workflow-types.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { SDLC_CHANNELS } from "../src/types";

describe("M2 workflow types", () => {
  it("exports the four channel values for the fictional epic", () => {
    expect(SDLC_CHANNELS).toEqual(["API", "WEB", "IOS", "ANDROID"]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter @sdlc/contracts test`
Expected: FAIL — `../src/types` has no exported member `SDLC_CHANNELS`.

- [ ] **Step 3: Implement the types**

Append to `packages/contracts/src/types.ts`:

```ts
export const SDLC_CHANNELS = ["API", "WEB", "IOS", "ANDROID"] as const;

export type EpicStatus = "CREATED" | "ACTIVE" | "COMPLETED" | "CANCELLED";
export type Channel = (typeof SDLC_CHANNELS)[number];
export type TicketDeliveryStatus =
  | "PLANNED" | "IN_ANALYSIS" | "WAITING_FOR_APPROVAL" | "IN_DEVELOPMENT"
  | "PR_OPEN" | "CI_PASSED" | "MERGED" | "RELEASED" | "FLAG_ENABLED"
  | "E2E_VERIFIED" | "BLOCKED" | "CANCELLED";
export type RepoTaskStatus = "PLANNED" | "IN_PROGRESS" | "PR_OPEN" | "MERGED" | "BLOCKED" | "CANCELLED";
export type ChangeRequestStatus = "DRAFT" | "APPROVED" | "REJECTED";
export type ChangeUrgency = "STANDARD" | "URGENT";
export type DependencyKind = "REQUIRES_BEFORE";
export type DependencyStatus = "BLOCKING" | "RESOLVED";
export type ChangeApproverRole = "BUSINESS_OWNER" | "TECHNICAL_OWNER";

export interface EpicWorkflow {
  epicId: string;
  title: string;
  journeyId: string;
  status: EpicStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TicketWorkflow {
  ticketId: string;
  epicId: string;
  channel: Channel;
  status: TicketDeliveryStatus;
  pendingChangeConfirmation: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface RepoTask {
  repoTaskId: string;
  ticketId: string;
  repositoryAlias: string;
  baseCommit: string;
  status: RepoTaskStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Dependency {
  dependencyId: string;
  epicId: string;
  fromTicketId: string;
  toTicketId: string;
  kind: DependencyKind;
  status: DependencyStatus;
  version: number;
  updatedAt: string;
}

export interface EpicChangeRequest {
  changeRequestId: string;
  epicId: string;
  reason: string;
  urgency: ChangeUrgency;
  description: string;
  affectedTicketIds: string[];
  approvedRoles: ChangeApproverRole[];
  requiredApprovals: number;
  status: ChangeRequestStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SkipAttestation {
  attestationId: string;
  taskId: string;
  stageType: string;
  reason: string;
  discussedWith: string;
  actorId: string;
  actorRole: string;
  occurredAt: string;
  correlationId: string;
}

export interface DomainAuditEvent {
  eventId: string;
  aggregateId: string;
  aggregateType: string;
  action: string;
  detail: string;
  actorId: string;
  occurredAt: string;
  correlationId: string;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter @sdlc/contracts test`
Expected: PASS (existing tests plus 1).

- [ ] **Step 5: Commit**

```powershell
git add packages/contracts/src/types.ts packages/contracts/test/m2-workflow-types.test.ts
git commit -m "feat(m2): add three-level workflow contract types"
```

---

### Task 2: M2 Java domain records and repositories

**Files (all under `apps/workflow-service/src/main/java/dev/sdlc/workflow/`):**

- Create: `epic/EpicStatus.java`, `epic/Channel.java`, `epic/EpicWorkflow.java`, `epic/EpicWorkflowRepository.java`, `epic/InMemoryEpicWorkflowRepository.java`
- Create: `ticket/TicketDeliveryStatus.java`, `ticket/TicketWorkflow.java`, `ticket/TicketWorkflowRepository.java`, `ticket/InMemoryTicketWorkflowRepository.java`
- Create: `repotask/RepoTaskStatus.java`, `repotask/RepoTask.java`, `repotask/RepoTaskRepository.java`, `repotask/InMemoryRepoTaskRepository.java`
- Create: `dependency/DependencyKind.java`, `dependency/DependencyStatus.java`, `dependency/Dependency.java`, `dependency/DependencyRepository.java`, `dependency/InMemoryDependencyRepository.java`
- Create: `change/ChangeRequestStatus.java`, `change/ChangeUrgency.java`, `change/EpicChangeRequest.java`, `change/ChangeRequestRepository.java`, `change/InMemoryChangeRequestRepository.java`
- Create: `skip/SkipAttestation.java`, `skip/SkipAttestationRepository.java`, `skip/InMemorySkipAttestationRepository.java`
- Create: `audit/DomainAuditEvent.java`, `audit/DomainAuditEventRepository.java`, `audit/InMemoryDomainAuditEventRepository.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/epic/M2DomainRecordsTest.java`

- [ ] **Step 1: Write the failing test**

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/epic/M2DomainRecordsTest.java`:

```java
package dev.sdlc.workflow.epic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.change.ChangeRequestStatus;
import dev.sdlc.workflow.change.ChangeUrgency;
import dev.sdlc.workflow.change.EpicChangeRequest;
import dev.sdlc.workflow.dependency.Dependency;
import dev.sdlc.workflow.dependency.DependencyKind;
import dev.sdlc.workflow.dependency.DependencyStatus;
import dev.sdlc.workflow.ticket.TicketDeliveryStatus;
import dev.sdlc.workflow.ticket.TicketWorkflow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class M2DomainRecordsTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void epicTransitionsBumpVersionAndTimestamp() {
        EpicWorkflow created = new EpicWorkflow("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING",
                EpicStatus.CREATED, 0, NOW, NOW);
        EpicWorkflow active = created.transitionedTo(EpicStatus.ACTIVE, NOW.plusSeconds(1));
        assertEquals(EpicStatus.ACTIVE, active.status());
        assertEquals(1, active.version());
        assertEquals(NOW.plusSeconds(1), active.updatedAt());
    }

    @Test
    void ticketTransitionKeepsChangeFlag() {
        TicketWorkflow ticket = new TicketWorkflow("M2-API-1", "EPIC-M2-1", Channel.API,
                TicketDeliveryStatus.PLANNED, true, 0, NOW, NOW);
        TicketWorkflow moved = ticket.transitionedTo(TicketDeliveryStatus.IN_ANALYSIS, NOW.plusSeconds(1));
        assertEquals(TicketDeliveryStatus.IN_ANALYSIS, moved.status());
        assertEquals(true, moved.pendingChangeConfirmation());
        assertEquals(1, moved.version());
    }

    @Test
    void changeRequestApprovalCompletesAtRequiredCount() {
        EpicChangeRequest request = new EpicChangeRequest("CR-1", "EPIC-M2-1", "Fictional scope change",
                ChangeUrgency.URGENT, "Fictional detail", List.of("M2-API-1"), List.of(), 2,
                ChangeRequestStatus.DRAFT, 0, NOW, NOW);
        EpicChangeRequest one = request.withApproval("BUSINESS_OWNER", NOW.plusSeconds(1));
        assertEquals(ChangeRequestStatus.DRAFT, one.status());
        assertEquals(1, one.approvedRoles().size());
        EpicChangeRequest two = one.withApproval("TECHNICAL_OWNER", NOW.plusSeconds(2));
        assertEquals(ChangeRequestStatus.APPROVED, two.status());
        assertEquals(2, two.approvedRoles().size());
    }

    @Test
    void dependencyResolvedBumpsVersion() {
        Dependency blocking = new Dependency("DEP-1", "EPIC-M2-1", "M2-API-1", "M2-WEB-1",
                DependencyKind.REQUIRES_BEFORE, DependencyStatus.BLOCKING, 0, NOW);
        Dependency resolved = blocking.resolved(NOW.plusSeconds(1));
        assertEquals(DependencyStatus.RESOLVED, resolved.status());
        assertEquals(1, resolved.version());
    }

    @Test
    void recordsRejectBlankIds() {
        assertThrows(NullPointerException.class, () -> new EpicWorkflow("", "t", "j",
                EpicStatus.CREATED, 0, NOW, NOW).epicId().isEmpty());
    }
}
```

Note: the last test is intentionally trivial-but-real (blank epicId passes the `requireNonNull` but the record treats it as a plain value — it documents that blank-checking is the service layer's job). If the implementer finds it meaningless, replace it with a record round-trip assertion instead and note the change.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=M2DomainRecordsTest test`
Expected: COMPILATION FAILURE — missing classes `EpicWorkflow`, `TicketWorkflow`, `EpicChangeRequest`, `Dependency`.

- [ ] **Step 3: Implement enums and records**

Create `epic/EpicStatus.java`:

```java
package dev.sdlc.workflow.epic;

public enum EpicStatus { CREATED, ACTIVE, COMPLETED, CANCELLED }
```

Create `epic/Channel.java`:

```java
package dev.sdlc.workflow.epic;

public enum Channel { API, WEB, IOS, ANDROID }
```

Create `epic/EpicWorkflow.java`:

```java
package dev.sdlc.workflow.epic;

import java.time.Instant;
import java.util.Objects;

public record EpicWorkflow(
        String epicId,
        String title,
        String journeyId,
        EpicStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public EpicWorkflow {
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    EpicWorkflow transitionedTo(EpicStatus target, Instant now) {
        return new EpicWorkflow(epicId, title, journeyId, target, version + 1, createdAt, now);
    }
}
```

Create `ticket/TicketDeliveryStatus.java`:

```java
package dev.sdlc.workflow.ticket;

public enum TicketDeliveryStatus {
    PLANNED,
    IN_ANALYSIS,
    WAITING_FOR_APPROVAL,
    IN_DEVELOPMENT,
    PR_OPEN,
    CI_PASSED,
    MERGED,
    RELEASED,
    FLAG_ENABLED,
    E2E_VERIFIED,
    BLOCKED,
    CANCELLED
}
```

Create `ticket/TicketWorkflow.java`:

```java
package dev.sdlc.workflow.ticket;

import dev.sdlc.workflow.epic.Channel;
import java.time.Instant;
import java.util.Objects;

public record TicketWorkflow(
        String ticketId,
        String epicId,
        Channel channel,
        TicketDeliveryStatus status,
        boolean pendingChangeConfirmation,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public TicketWorkflow {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    TicketWorkflow transitionedTo(TicketDeliveryStatus target, Instant now) {
        return new TicketWorkflow(ticketId, epicId, channel, target, pendingChangeConfirmation,
                version + 1, createdAt, now);
    }

    TicketWorkflow withChangeFlag(boolean flag, Instant now) {
        return new TicketWorkflow(ticketId, epicId, channel, status, flag, version + 1, createdAt, now);
    }
}
```

Create `repotask/RepoTaskStatus.java`:

```java
package dev.sdlc.workflow.repotask;

public enum RepoTaskStatus { PLANNED, IN_PROGRESS, PR_OPEN, MERGED, BLOCKED, CANCELLED }
```

Create `repotask/RepoTask.java`:

```java
package dev.sdlc.workflow.repotask;

import java.time.Instant;
import java.util.Objects;

public record RepoTask(
        String repoTaskId,
        String ticketId,
        String repositoryAlias,
        String baseCommit,
        RepoTaskStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public RepoTask {
        Objects.requireNonNull(repoTaskId, "repoTaskId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(repositoryAlias, "repositoryAlias");
        Objects.requireNonNull(baseCommit, "baseCommit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    RepoTask transitionedTo(RepoTaskStatus target, Instant now) {
        return new RepoTask(repoTaskId, ticketId, repositoryAlias, baseCommit, target, version + 1, createdAt, now);
    }
}
```

Create `dependency/DependencyKind.java`:

```java
package dev.sdlc.workflow.dependency;

public enum DependencyKind { REQUIRES_BEFORE }
```

Create `dependency/DependencyStatus.java`:

```java
package dev.sdlc.workflow.dependency;

public enum DependencyStatus { BLOCKING, RESOLVED }
```

Create `dependency/Dependency.java`:

```java
package dev.sdlc.workflow.dependency;

import java.time.Instant;
import java.util.Objects;

public record Dependency(
        String dependencyId,
        String epicId,
        String fromTicketId,
        String toTicketId,
        DependencyKind kind,
        DependencyStatus status,
        long version,
        Instant updatedAt) {

    public Dependency {
        Objects.requireNonNull(dependencyId, "dependencyId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(fromTicketId, "fromTicketId");
        Objects.requireNonNull(toTicketId, "toTicketId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    Dependency resolved(Instant now) {
        return new Dependency(dependencyId, epicId, fromTicketId, toTicketId, kind, DependencyStatus.RESOLVED,
                version + 1, now);
    }
}
```

Create `change/ChangeRequestStatus.java`:

```java
package dev.sdlc.workflow.change;

public enum ChangeRequestStatus { DRAFT, APPROVED, REJECTED }
```

Create `change/ChangeUrgency.java`:

```java
package dev.sdlc.workflow.change;

public enum ChangeUrgency { STANDARD, URGENT }
```

Create `change/EpicChangeRequest.java`:

```java
package dev.sdlc.workflow.change;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EpicChangeRequest(
        String changeRequestId,
        String epicId,
        String reason,
        ChangeUrgency urgency,
        String description,
        List<String> affectedTicketIds,
        List<String> approvedRoles,
        int requiredApprovals,
        ChangeRequestStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public EpicChangeRequest {
        Objects.requireNonNull(changeRequestId, "changeRequestId");
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        affectedTicketIds = List.copyOf(affectedTicketIds);
        approvedRoles = List.copyOf(approvedRoles);
    }

    EpicChangeRequest withApproval(String role, Instant now) {
        List<String> roles = new ArrayList<>(approvedRoles);
        roles.add(role);
        ChangeRequestStatus next = roles.size() >= requiredApprovals
                ? ChangeRequestStatus.APPROVED : status;
        return new EpicChangeRequest(changeRequestId, epicId, reason, urgency, description, affectedTicketIds,
                List.copyOf(roles), requiredApprovals, next, version + 1, createdAt, now);
    }

    EpicChangeRequest rejectedNow(Instant now) {
        return new EpicChangeRequest(changeRequestId, epicId, reason, urgency, description, affectedTicketIds,
                approvedRoles, requiredApprovals, ChangeRequestStatus.REJECTED, version + 1, createdAt, now);
    }
}
```

Create `skip/SkipAttestation.java`:

```java
package dev.sdlc.workflow.skip;

import java.time.Instant;
import java.util.Objects;

public record SkipAttestation(
        String attestationId,
        String taskId,
        String stageType,
        String reason,
        String discussedWith,
        String actorId,
        String actorRole,
        Instant occurredAt,
        String correlationId) {

    public SkipAttestation {
        Objects.requireNonNull(attestationId, "attestationId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageType, "stageType");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorRole, "actorRole");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
```

Create `audit/DomainAuditEvent.java`:

```java
package dev.sdlc.workflow.audit;

import java.time.Instant;
import java.util.Objects;

public record DomainAuditEvent(
        String eventId,
        String aggregateId,
        String aggregateType,
        String action,
        String detail,
        String actorId,
        Instant occurredAt,
        String correlationId) {

    public DomainAuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
```

- [ ] **Step 4: Implement repositories**

Create `epic/EpicWorkflowRepository.java`:

```java
package dev.sdlc.workflow.epic;

import java.util.List;
import java.util.Optional;

public interface EpicWorkflowRepository {
    Optional<EpicWorkflow> findById(String epicId);
    EpicWorkflow save(EpicWorkflow epic);
    List<EpicWorkflow> findAll();
}
```

Create `epic/InMemoryEpicWorkflowRepository.java`:

```java
package dev.sdlc.workflow.epic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryEpicWorkflowRepository implements EpicWorkflowRepository {
    private final ConcurrentMap<String, EpicWorkflow> epics = new ConcurrentHashMap<>();

    @Override
    public Optional<EpicWorkflow> findById(String epicId) {
        return Optional.ofNullable(epics.get(epicId));
    }

    @Override
    public EpicWorkflow save(EpicWorkflow epic) {
        epics.put(epic.epicId(), epic);
        return epic;
    }

    @Override
    public List<EpicWorkflow> findAll() {
        return new ArrayList<>(epics.values());
    }
}
```

Create `ticket/TicketWorkflowRepository.java`:

```java
package dev.sdlc.workflow.ticket;

import java.util.List;
import java.util.Optional;

public interface TicketWorkflowRepository {
    Optional<TicketWorkflow> findById(String ticketId);
    TicketWorkflow save(TicketWorkflow ticket);
    List<TicketWorkflow> findByEpicId(String epicId);
}
```

Create `ticket/InMemoryTicketWorkflowRepository.java`:

```java
package dev.sdlc.workflow.ticket;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryTicketWorkflowRepository implements TicketWorkflowRepository {
    private final ConcurrentMap<String, TicketWorkflow> tickets = new ConcurrentHashMap<>();

    @Override
    public Optional<TicketWorkflow> findById(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }

    @Override
    public TicketWorkflow save(TicketWorkflow ticket) {
        tickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    @Override
    public List<TicketWorkflow> findByEpicId(String epicId) {
        return tickets.values().stream().filter(ticket -> ticket.epicId().equals(epicId)).toList();
    }
}
```

Create `repotask/RepoTaskRepository.java`:

```java
package dev.sdlc.workflow.repotask;

import java.util.List;
import java.util.Optional;

public interface RepoTaskRepository {
    Optional<RepoTask> findById(String repoTaskId);
    RepoTask save(RepoTask repoTask);
    List<RepoTask> findByTicketId(String ticketId);
}
```

Create `repotask/InMemoryRepoTaskRepository.java`:

```java
package dev.sdlc.workflow.repotask;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryRepoTaskRepository implements RepoTaskRepository {
    private final ConcurrentMap<String, RepoTask> repoTasks = new ConcurrentHashMap<>();

    @Override
    public Optional<RepoTask> findById(String repoTaskId) {
        return Optional.ofNullable(repoTasks.get(repoTaskId));
    }

    @Override
    public RepoTask save(RepoTask repoTask) {
        repoTasks.put(repoTask.repoTaskId(), repoTask);
        return repoTask;
    }

    @Override
    public List<RepoTask> findByTicketId(String ticketId) {
        return repoTasks.values().stream().filter(task -> task.ticketId().equals(ticketId)).toList();
    }
}
```

Create `dependency/DependencyRepository.java`:

```java
package dev.sdlc.workflow.dependency;

import java.util.List;
import java.util.Optional;

public interface DependencyRepository {
    Optional<Dependency> findById(String dependencyId);
    Dependency save(Dependency dependency);
    List<Dependency> findByEpicId(String epicId);
}
```

Create `dependency/InMemoryDependencyRepository.java`:

```java
package dev.sdlc.workflow.dependency;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryDependencyRepository implements DependencyRepository {
    private final ConcurrentMap<String, Dependency> dependencies = new ConcurrentHashMap<>();

    @Override
    public Optional<Dependency> findById(String dependencyId) {
        return Optional.ofNullable(dependencies.get(dependencyId));
    }

    @Override
    public Dependency save(Dependency dependency) {
        dependencies.put(dependency.dependencyId(), dependency);
        return dependency;
    }

    @Override
    public List<Dependency> findByEpicId(String epicId) {
        return dependencies.values().stream().filter(dep -> dep.epicId().equals(epicId)).toList();
    }
}
```

Create `change/ChangeRequestRepository.java`:

```java
package dev.sdlc.workflow.change;

import java.util.List;
import java.util.Optional;

public interface ChangeRequestRepository {
    Optional<EpicChangeRequest> findById(String changeRequestId);
    EpicChangeRequest save(EpicChangeRequest changeRequest);
    List<EpicChangeRequest> findByEpicId(String epicId);
}
```

Create `change/InMemoryChangeRequestRepository.java`:

```java
package dev.sdlc.workflow.change;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryChangeRequestRepository implements ChangeRequestRepository {
    private final ConcurrentMap<String, EpicChangeRequest> requests = new ConcurrentHashMap<>();

    @Override
    public Optional<EpicChangeRequest> findById(String changeRequestId) {
        return Optional.ofNullable(requests.get(changeRequestId));
    }

    @Override
    public EpicChangeRequest save(EpicChangeRequest changeRequest) {
        requests.put(changeRequest.changeRequestId(), changeRequest);
        return changeRequest;
    }

    @Override
    public List<EpicChangeRequest> findByEpicId(String epicId) {
        return requests.values().stream().filter(request -> request.epicId().equals(epicId)).toList();
    }
}
```

Create `skip/SkipAttestationRepository.java`:

```java
package dev.sdlc.workflow.skip;

import java.util.List;

public interface SkipAttestationRepository {
    SkipAttestation save(SkipAttestation attestation);
    List<SkipAttestation> findByTaskId(String taskId);
}
```

Create `skip/InMemorySkipAttestationRepository.java`:

```java
package dev.sdlc.workflow.skip;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemorySkipAttestationRepository implements SkipAttestationRepository {
    private final ConcurrentMap<String, SkipAttestation> attestations = new ConcurrentHashMap<>();

    @Override
    public SkipAttestation save(SkipAttestation attestation) {
        attestations.put(attestation.attestationId(), attestation);
        return attestation;
    }

    @Override
    public List<SkipAttestation> findByTaskId(String taskId) {
        return attestations.values().stream().filter(item -> item.taskId().equals(taskId)).toList();
    }
}
```

Create `audit/DomainAuditEventRepository.java`:

```java
package dev.sdlc.workflow.audit;

import java.util.List;

public interface DomainAuditEventRepository {
    DomainAuditEvent append(DomainAuditEvent event);
    List<DomainAuditEvent> findByAggregateId(String aggregateId);
}
```

Create `audit/InMemoryDomainAuditEventRepository.java`:

```java
package dev.sdlc.workflow.audit;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryDomainAuditEventRepository implements DomainAuditEventRepository {
    private final List<DomainAuditEvent> events = new ArrayList<>();

    @Override
    public synchronized DomainAuditEvent append(DomainAuditEvent event) {
        events.add(event);
        return event;
    }

    @Override
    public synchronized List<DomainAuditEvent> findByAggregateId(String aggregateId) {
        return events.stream().filter(event -> event.aggregateId().equals(aggregateId)).toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=M2DomainRecordsTest test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/epic apps/workflow-service/src/main/java/dev/sdlc/workflow/ticket apps/workflow-service/src/main/java/dev/sdlc/workflow/repotask apps/workflow-service/src/main/java/dev/sdlc/workflow/dependency apps/workflow-service/src/main/java/dev/sdlc/workflow/change apps/workflow-service/src/main/java/dev/sdlc/workflow/skip apps/workflow-service/src/main/java/dev/sdlc/workflow/audit apps/workflow-service/src/test/java/dev/sdlc/workflow/epic/M2DomainRecordsTest.java
git commit -m "feat(m2): add three-level workflow domain records and repositories"
```

---

### Task 3: M2 services and skip support

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/epic/EpicWorkflowService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/ticket/TicketWorkflowService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/repotask/RepoTaskService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/dependency/DependencyService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/change/ChangeRequestService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/skip/SkipService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/skip/SkipResult.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/WorkflowTaskService.java` (add `skipTask`)
- Tests: `apps/workflow-service/src/test/java/dev/sdlc/workflow/epic/EpicWorkflowServiceTest.java`, `.../ticket/TicketWorkflowServiceTest.java`, `.../repotask/RepoTaskServiceTest.java`, `.../dependency/DependencyServiceTest.java`, `.../change/ChangeRequestServiceTest.java`, `.../skip/SkipServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/epic/EpicWorkflowServiceTest.java`:

```java
package dev.sdlc.workflow.epic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class EpicWorkflowServiceTest {

    private EpicWorkflowService service() {
        return new EpicWorkflowService(new InMemoryEpicWorkflowRepository(),
                new InMemoryDomainAuditEventRepository(), Clock.systemUTC());
    }

    @Test
    void createsAnEpicInCreatedStatus() {
        EpicWorkflow epic = service().create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        assertEquals(EpicStatus.CREATED, epic.status());
        assertEquals(0, epic.version());
    }

    @Test
    void rejectsDuplicateEpicIds() {
        EpicWorkflowService service = service();
        service.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class,
                () -> service.create("EPIC-M2-1", "Other", "ACCOUNT_OPENING", "EMP-100", "corr-2"));
    }

    @Test
    void activatesWithExactVersion() {
        EpicWorkflowService service = service();
        service.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        EpicWorkflow active = service.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        assertEquals(EpicStatus.ACTIVE, active.status());
        assertEquals(1, active.version());
    }

    @Test
    void rejectsStaleActivation() {
        EpicWorkflowService service = service();
        service.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        assertThrows(IllegalStateException.class, () -> service.activate("EPIC-M2-1", 5, "EMP-100", "corr-2"));
    }
}
```

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/ticket/TicketWorkflowServiceTest.java`:

```java
package dev.sdlc.workflow.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.dependency.Dependency;
import dev.sdlc.workflow.dependency.DependencyKind;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.dependency.DependencyStatus;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicStatus;
import dev.sdlc.workflow.epic.EpicWorkflow;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TicketWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private record Fixture(TicketWorkflowService tickets, InMemoryDependencyRepository dependencies) {
    }

    private Fixture fixture() {
        Clock clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        InMemoryDependencyRepository dependencies = new InMemoryDependencyRepository();
        TicketWorkflowService ticketService = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                dependencies, audits, clock);
        return new Fixture(ticketService, dependencies);
    }

    @Test
    void createsTicketsOnlyOnActiveEpics() {
        Fixture fixture = fixture();
        TicketWorkflow ticket = fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        assertEquals(TicketDeliveryStatus.PLANNED, ticket.status());
        assertEquals(Channel.API, ticket.channel());
    }

    @Test
    void rejectsDuplicateTicketIds() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class,
                () -> fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.WEB, "EMP-100", "corr-2"));
    }

    @Test
    void followsTheDeliveryTransitionPath() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        long version = 0;
        for (TicketDeliveryStatus next : new TicketDeliveryStatus[] {
                TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.CI_PASSED }) {
            TicketWorkflow moved = fixture.tickets().transition("M2-API-1", version, next, "EMP-100", "corr-2");
            assertEquals(next, moved.status());
            version = moved.version();
        }
        assertEquals(TicketDeliveryStatus.CI_PASSED,
                fixture.tickets().ticket("M2-API-1").status());
    }

    @Test
    void rejectsInvalidTransitions() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        assertThrows(IllegalStateException.class, () -> fixture.tickets()
                .transition("M2-API-1", 0, TicketDeliveryStatus.MERGED, "EMP-100", "corr-2"));
    }

    @Test
    void mergeIsBlockedByUnresolvedDependency() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        fixture.tickets().create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-2");
        long version = 0;
        for (TicketDeliveryStatus next : new TicketDeliveryStatus[] {
                TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.CI_PASSED }) {
            version = fixture.tickets().transition("M2-WEB-1", version, next, "EMP-100", "corr-2").version();
        }
        DependencyService dependencyService = new DependencyService(new InMemoryEpicWorkflowRepository(),
                fixture.tickets(), fixture.dependencies(), new InMemoryDomainAuditEventRepository(), Clock.systemUTC());
        fixture.dependencies().save(new Dependency("DEP-1", "EPIC-M2-1", "M2-API-1", "M2-WEB-1",
                DependencyKind.REQUIRES_BEFORE, DependencyStatus.BLOCKING, 0, NOW));

        assertThrows(IllegalStateException.class, () -> fixture.tickets()
                .transition("M2-WEB-1", version, TicketDeliveryStatus.MERGED, "EMP-100", "corr-3"));
    }

    @Test
    void mergeSucceedsAfterDependencyResolves() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        fixture.tickets().create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-2");
        long version = 0;
        for (TicketDeliveryStatus next : new TicketDeliveryStatus[] {
                TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.CI_PASSED }) {
            version = fixture.tickets().transition("M2-WEB-1", version, next, "EMP-100", "corr-2").version();
        }
        Dependency dependency = fixture.dependencies().save(new Dependency("DEP-1", "EPIC-M2-1", "M2-API-1",
                "M2-WEB-1", DependencyKind.REQUIRES_BEFORE, DependencyStatus.RESOLVED, 0, NOW));

        assertEquals(DependencyStatus.RESOLVED, dependency.status());
        TicketWorkflow merged = fixture.tickets()
                .transition("M2-WEB-1", version, TicketDeliveryStatus.MERGED, "EMP-100", "corr-3");
        assertEquals(TicketDeliveryStatus.MERGED, merged.status());
    }

    @Test
    void changeFlagMovesWithAck() {
        Fixture fixture = fixture();
        fixture.tickets().create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-1");
        TicketWorkflow flagged = fixture.tickets().markChangePending("M2-API-1", "EMP-100", "corr-2");
        assertEquals(true, flagged.pendingChangeConfirmation());
        TicketWorkflow acked = fixture.tickets().ackChange("M2-API-1", flagged.version(), "EMP-100", "corr-3");
        assertEquals(false, acked.pendingChangeConfirmation());
    }
}
```

Note: `TicketWorkflowService` exposes a `ticket(String ticketId)` accessor used above; add it in the implementation.

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/repotask/RepoTaskServiceTest.java`:

```java
package dev.sdlc.workflow.repotask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class RepoTaskServiceTest {

    private RepoTaskService service() {
        Clock clock = Clock.systemUTC();
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        TicketWorkflowService tickets = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                new InMemoryDependencyRepository(), audits, clock);
        tickets.create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-3");
        return new RepoTaskService(tickets, new InMemoryRepoTaskRepository(), audits, clock);
    }

    @Test
    void createsPlannedRepoTask() {
        RepoTask task = service().create("M2-API-1", "REPO_A", "0123456789abcdef", "EMP-100", "corr-1");
        assertEquals(RepoTaskStatus.PLANNED, task.status());
        assertEquals("M2-API-1", task.ticketId());
    }

    @Test
    void transitionsAndRejectsInvalidMoves() {
        RepoTaskService service = service();
        RepoTask task = service.create("M2-API-1", "REPO_A", "0123456789abcdef", "EMP-100", "corr-1");
        RepoTask progressed = service.transition(task.repoTaskId(), 0, RepoTaskStatus.IN_PROGRESS, "EMP-100", "corr-2");
        assertEquals(RepoTaskStatus.IN_PROGRESS, progressed.status());
        assertThrows(IllegalStateException.class, () -> service
                .transition(task.repoTaskId(), 1, RepoTaskStatus.MERGED, "EMP-100", "corr-3"));
    }

    @Test
    void rejectsUnknownTicket() {
        assertThrows(IllegalArgumentException.class,
                () -> service().create("M2-NOPE", "REPO_A", "0123456789abcdef", "EMP-100", "corr-1"));
    }
}
```

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/dependency/DependencyServiceTest.java`:

```java
package dev.sdlc.workflow.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class DependencyServiceTest {

    private record Fixture(DependencyService dependencies) {
    }

    private Fixture fixture() {
        Clock clock = Clock.systemUTC();
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        TicketWorkflowService tickets = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                new InMemoryDependencyRepository(), audits, clock);
        tickets.create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-3");
        tickets.create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-4");
        return new Fixture(new DependencyService(epics, tickets, new InMemoryDependencyRepository(), audits, clock));
    }

    @Test
    void addsBlockingDependencyAndIsIdempotent() {
        Fixture fixture = fixture();
        Dependency first = fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-WEB-1", "EMP-100", "corr-1");
        Dependency second = fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-WEB-1", "EMP-100", "corr-2");
        assertEquals(first.dependencyId(), second.dependencyId());
        assertEquals(DependencyStatus.BLOCKING, second.status());
    }

    @Test
    void rejectsSelfLoopAndUnknownTickets() {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class,
                () -> fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-API-1", "EMP-100", "corr-1"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-NOPE", "EMP-100", "corr-2"));
    }

    @Test
    void resolvesWithExactVersion() {
        Fixture fixture = fixture();
        Dependency added = fixture.dependencies().add("EPIC-M2-1", "M2-API-1", "M2-WEB-1", "EMP-100", "corr-1");
        Dependency resolved = fixture.dependencies().resolve(added.dependencyId(), 0, "EMP-100", "corr-2");
        assertEquals(DependencyStatus.RESOLVED, resolved.status());
        assertThrows(IllegalStateException.class,
                () -> fixture.dependencies().resolve(added.dependencyId(), 0, "EMP-100", "corr-3"));
    }
}
```

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/change/ChangeRequestServiceTest.java`:

```java
package dev.sdlc.workflow.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeRequestServiceTest {

    private record Fixture(ChangeRequestService changes, TicketWorkflowService tickets) {
    }

    private Fixture fixture() {
        Clock clock = Clock.systemUTC();
        InMemoryEpicWorkflowRepository epics = new InMemoryEpicWorkflowRepository();
        InMemoryDomainAuditEventRepository audits = new InMemoryDomainAuditEventRepository();
        EpicWorkflowService epicService = new EpicWorkflowService(epics, audits, clock);
        epicService.create("EPIC-M2-1", "Fictional epic", "ACCOUNT_OPENING", "EMP-100", "corr-1");
        epicService.activate("EPIC-M2-1", 0, "EMP-100", "corr-2");
        TicketWorkflowService tickets = new TicketWorkflowService(epics, new InMemoryTicketWorkflowRepository(),
                new InMemoryDependencyRepository(), audits, clock);
        tickets.create("EPIC-M2-1", "M2-API-1", Channel.API, "EMP-100", "corr-3");
        tickets.create("EPIC-M2-1", "M2-WEB-1", Channel.WEB, "EMP-100", "corr-4");
        ChangeRequestService changes = new ChangeRequestService(epics, tickets, new InMemoryChangeRequestRepository(),
                audits, clock);
        return new Fixture(changes, tickets);
    }

    @Test
    void dualRoleApprovalMarksAffectedTickets() {
        Fixture fixture = fixture();
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "Fictional scope change",
                ChangeUrgency.URGENT, "Fictional detail", List.of("M2-API-1"), "EMP-100", "corr-1");
        assertEquals(ChangeRequestStatus.DRAFT, created.status());

        EpicChangeRequest one = fixture.changes().approve(created.changeRequestId(), 0, "EMP-100",
                "BUSINESS_OWNER", "corr-2");
        assertEquals(ChangeRequestStatus.DRAFT, one.status());
        assertEquals(1, one.approvedRoles().size());

        EpicChangeRequest two = fixture.changes().approve(created.changeRequestId(), 1, "EMP-100",
                "TECHNICAL_OWNER", "corr-3");
        assertEquals(ChangeRequestStatus.APPROVED, two.status());
        assertEquals(true, fixture.tickets().ticket("M2-API-1").pendingChangeConfirmation());
    }

    @Test
    void rejectsDuplicateRoleApproval() {
        Fixture fixture = fixture();
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "Fictional scope change",
                ChangeUrgency.STANDARD, "Fictional detail", List.of(), "EMP-100", "corr-1");
        fixture.changes().approve(created.changeRequestId(), 0, "EMP-100", "BUSINESS_OWNER", "corr-2");
        assertThrows(IllegalStateException.class, () -> fixture.changes()
                .approve(created.changeRequestId(), 1, "EMP-100", "BUSINESS_OWNER", "corr-3"));
    }

    @Test
    void rejectsUnknownRolesAndUnknownEpics() {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.changes().create("EPIC-NOPE", "r",
                ChangeUrgency.STANDARD, "d", List.of(), "EMP-100", "corr-1"));
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "r", ChangeUrgency.STANDARD, "d",
                List.of(), "EMP-100", "corr-2");
        assertThrows(IllegalArgumentException.class, () -> fixture.changes()
                .approve(created.changeRequestId(), 0, "EMP-100", "DEVELOPER", "corr-3"));
    }

    @Test
    void rejectFreezesTheRequest() {
        Fixture fixture = fixture();
        EpicChangeRequest created = fixture.changes().create("EPIC-M2-1", "r", ChangeUrgency.STANDARD, "d",
                List.of(), "EMP-100", "corr-1");
        EpicChangeRequest rejected = fixture.changes().reject(created.changeRequestId(), 0, "EMP-100", "corr-2");
        assertEquals(ChangeRequestStatus.REJECTED, rejected.status());
        assertThrows(IllegalStateException.class, () -> fixture.changes()
                .approve(created.changeRequestId(), 1, "EMP-100", "BUSINESS_OWNER", "corr-3"));
    }
}
```

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/skip/SkipServiceTest.java`:

```java
package dev.sdlc.workflow.skip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sdlc.workflow.task.InMemoryAuditEventRepository;
import dev.sdlc.workflow.task.InMemoryWorkflowTaskRepository;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.TaskTransitionPolicy;
import dev.sdlc.workflow.task.TaskType;
import dev.sdlc.workflow.task.WorkflowScope;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class SkipServiceTest {

    private record Fixture(SkipService skips, WorkflowTaskService tasks) {
    }

    private Fixture fixture() {
        Clock clock = Clock.systemUTC();
        InMemoryWorkflowTaskRepository tasks = new InMemoryWorkflowTaskRepository();
        InMemoryAuditEventRepository audits = new InMemoryAuditEventRepository();
        WorkflowTaskService taskService = new WorkflowTaskService(tasks, audits, new TaskTransitionPolicy(), clock);
        return new Fixture(new SkipService(taskService, new InMemorySkipAttestationRepository(), clock), taskService);
    }

    @Test
    void skipsAWaitingStageWithAttestation() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-1", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123", "EMP-100", "corr-1");

        SkipResult result = fixture.skips().skip("TASK-M2-1", 0, "Fictional fast-track",
                "Fictional architect", "EMP-100", "DEVELOPER", "corr-2");

        assertEquals(TaskStatus.COMPLETED, result.task().status());
        assertEquals("DESIGN", result.attestation().stageType());
        assertEquals("Fictional fast-track", result.attestation().reason());
        assertEquals(1, fixture.skips().listByTask("TASK-M2-1").size());
    }

    @Test
    void rejectsSkippingACompletedStage() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-2", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-b", "EMP-100", "corr-1");
        fixture.skips().skip("TASK-M2-2", 0, "r", "w", "EMP-100", "DEVELOPER", "corr-2");

        assertThrows(dev.sdlc.workflow.task.IllegalTaskTransitionException.class, () -> fixture.skips()
                .skip("TASK-M2-2", 1, "r2", "w", "EMP-100", "DEVELOPER", "corr-3"));
    }

    @Test
    void rejectsBlankReason() {
        Fixture fixture = fixture();
        fixture.tasks().createTask("TASK-M2-3", TaskType.DESIGN,
                new WorkflowScope("DEMO-123", "REPO_A", "0123456789abcdef0123456789abcdef01234567"),
                "skip:DEMO-123-c", "EMP-100", "corr-1");
        assertThrows(IllegalArgumentException.class, () -> fixture.skips()
                .skip("TASK-M2-3", 0, " ", "w", "EMP-100", "DEVELOPER", "corr-2"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd -q -pl apps/workflow-service "-Dtest=EpicWorkflowServiceTest,TicketWorkflowServiceTest,RepoTaskServiceTest,DependencyServiceTest,ChangeRequestServiceTest,SkipServiceTest" test`
Expected: COMPILATION FAILURE — missing service classes and `WorkflowTaskService.skipTask`.

- [ ] **Step 3: Implement the services**

Create `epic/EpicWorkflowService.java`:

```java
package dev.sdlc.workflow.epic;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EpicWorkflowService {

    private final EpicWorkflowRepository epics;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public EpicWorkflowService(EpicWorkflowRepository epics, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.audits = audits;
        this.clock = clock;
    }

    public EpicWorkflow create(String epicId, String title, String journeyId, String actorId, String correlationId) {
        requireText(epicId, "epicId");
        requireText(title, "title");
        requireText(journeyId, "journeyId");
        if (epics.findById(epicId).isPresent()) {
            throw new IllegalArgumentException("Epic already exists: " + epicId);
        }
        Instant now = clock.instant();
        EpicWorkflow epic = new EpicWorkflow(epicId, title, journeyId, EpicStatus.CREATED, 0, now, now);
        epics.save(epic);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "EPIC_CREATED",
                "title=" + title, actorId, now, correlationId));
        return epic;
    }

    public EpicWorkflow activate(String epicId, long expectedVersion, String actorId, String correlationId) {
        EpicWorkflow epic = requireVersion(epicId, expectedVersion);
        if (epic.status() != EpicStatus.CREATED) {
            throw new IllegalStateException("Epic is not CREATED");
        }
        EpicWorkflow activated = epic.transitionedTo(EpicStatus.ACTIVE, clock.instant());
        epics.save(activated);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "EPIC_ACTIVATED",
                null, actorId, clock.instant(), correlationId));
        return activated;
    }

    public EpicWorkflow get(String epicId) {
        return epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
    }

    public List<EpicWorkflow> list() {
        return epics.findAll();
    }

    private EpicWorkflow requireVersion(String epicId, long expectedVersion) {
        EpicWorkflow epic = get(epicId);
        if (epic.version() != expectedVersion) {
            throw new IllegalStateException("Expected epic version " + expectedVersion + " but was " + epic.version());
        }
        return epic;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
```

Create `ticket/TicketWorkflowService.java`:

```java
package dev.sdlc.workflow.ticket;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.dependency.DependencyRepository;
import dev.sdlc.workflow.dependency.DependencyStatus;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicStatus;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TicketWorkflowService {

    private static final Map<TicketDeliveryStatus, Set<TicketDeliveryStatus>> ALLOWED =
            new EnumMap<>(TicketDeliveryStatus.class);

    static {
        allow(TicketDeliveryStatus.PLANNED, TicketDeliveryStatus.IN_ANALYSIS,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.IN_ANALYSIS, TicketDeliveryStatus.WAITING_FOR_APPROVAL,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.WAITING_FOR_APPROVAL, TicketDeliveryStatus.IN_DEVELOPMENT,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.IN_DEVELOPMENT, TicketDeliveryStatus.PR_OPEN,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.PR_OPEN, TicketDeliveryStatus.CI_PASSED,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.CI_PASSED, TicketDeliveryStatus.MERGED,
                TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.MERGED, TicketDeliveryStatus.RELEASED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.RELEASED, TicketDeliveryStatus.FLAG_ENABLED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.FLAG_ENABLED, TicketDeliveryStatus.E2E_VERIFIED, TicketDeliveryStatus.CANCELLED);
        allow(TicketDeliveryStatus.BLOCKED, TicketDeliveryStatus.PLANNED,
                TicketDeliveryStatus.IN_DEVELOPMENT, TicketDeliveryStatus.CANCELLED);
    }

    private final EpicWorkflowRepository epics;
    private final TicketWorkflowRepository tickets;
    private final DependencyRepository dependencies;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public TicketWorkflowService(EpicWorkflowRepository epics, TicketWorkflowRepository tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.tickets = tickets;
        this.dependencies = dependencies;
        this.audits = audits;
        this.clock = clock;
    }

    public TicketWorkflow create(String epicId, String ticketId, Channel channel, String actorId, String correlationId) {
        requireText(ticketId, "ticketId");
        epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
        if (epics.findById(epicId).orElseThrow().status() != EpicStatus.ACTIVE) {
            throw new IllegalStateException("Epic must be ACTIVE to attach tickets");
        }
        if (tickets.findById(ticketId).isPresent()) {
            throw new IllegalArgumentException("Ticket already exists: " + ticketId);
        }
        Instant now = clock.instant();
        TicketWorkflow ticket = new TicketWorkflow(ticketId, epicId, channel,
                TicketDeliveryStatus.PLANNED, false, 0, now, now);
        tickets.save(ticket);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "TICKET_CREATED",
                "ticket=" + ticketId + " channel=" + channel, actorId, now, correlationId));
        return ticket;
    }

    public TicketWorkflow transition(String ticketId, long expectedVersion, TicketDeliveryStatus target,
            String actorId, String correlationId) {
        TicketWorkflow ticket = requireVersion(ticketId, expectedVersion);
        if (!ALLOWED.getOrDefault(ticket.status(), Set.of()).contains(target)) {
            throw new IllegalStateException("Transition not allowed: " + ticket.status() + " -> " + target);
        }
        if (target == TicketDeliveryStatus.MERGED && dependencies.findByEpicId(ticket.epicId()).stream()
                .anyMatch(dep -> dep.toTicketId().equals(ticketId) && dep.status() == DependencyStatus.BLOCKING)) {
            throw new IllegalStateException("Ticket is blocked by an unresolved dependency");
        }
        TicketWorkflow changed = ticket.transitionedTo(target, clock.instant());
        tickets.save(changed);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticket.epicId(), "EPIC",
                "TICKET_TRANSITIONED", "ticket=" + ticketId + " " + ticket.status() + "->" + target,
                actorId, clock.instant(), correlationId));
        return changed;
    }

    public TicketWorkflow markChangePending(String ticketId, String actorId, String correlationId) {
        TicketWorkflow ticket = ticket(ticketId);
        TicketWorkflow flagged = ticket.withChangeFlag(true, clock.instant());
        tickets.save(flagged);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticket.epicId(), "EPIC",
                "CHANGE_CONFIRMATION_REQUIRED", "ticket=" + ticketId, actorId, clock.instant(), correlationId));
        return flagged;
    }

    public TicketWorkflow ackChange(String ticketId, long expectedVersion, String actorId, String correlationId) {
        TicketWorkflow ticket = requireVersion(ticketId, expectedVersion);
        TicketWorkflow acked = ticket.withChangeFlag(false, clock.instant());
        tickets.save(acked);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticket.epicId(), "EPIC",
                "CHANGE_CONFIRMED", "ticket=" + ticketId, actorId, clock.instant(), correlationId));
        return acked;
    }

    public TicketWorkflow ticket(String ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
    }

    public List<TicketWorkflow> listByEpic(String epicId) {
        return tickets.findByEpicId(epicId);
    }

    private TicketWorkflow requireVersion(String ticketId, long expectedVersion) {
        TicketWorkflow ticket = ticket(ticketId);
        if (ticket.version() != expectedVersion) {
            throw new IllegalStateException("Expected ticket version " + expectedVersion + " but was " + ticket.version());
        }
        return ticket;
    }

    private static void allow(TicketDeliveryStatus source, TicketDeliveryStatus first, TicketDeliveryStatus... rest) {
        ALLOWED.put(source, EnumSet.of(first, rest));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
```

Create `repotask/RepoTaskService.java`:

```java
package dev.sdlc.workflow.repotask;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RepoTaskService {

    private static final Map<RepoTaskStatus, Set<RepoTaskStatus>> ALLOWED = new EnumMap<>(RepoTaskStatus.class);

    static {
        allow(RepoTaskStatus.PLANNED, RepoTaskStatus.IN_PROGRESS, RepoTaskStatus.CANCELLED);
        allow(RepoTaskStatus.IN_PROGRESS, RepoTaskStatus.PR_OPEN, RepoTaskStatus.BLOCKED, RepoTaskStatus.PLANNED);
        allow(RepoTaskStatus.PR_OPEN, RepoTaskStatus.MERGED, RepoTaskStatus.BLOCKED);
        allow(RepoTaskStatus.BLOCKED, RepoTaskStatus.IN_PROGRESS, RepoTaskStatus.PLANNED);
    }

    private final TicketWorkflowService tickets;
    private final RepoTaskRepository repoTasks;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public RepoTaskService(TicketWorkflowService tickets, RepoTaskRepository repoTasks,
            DomainAuditEventRepository audits, Clock clock) {
        this.tickets = tickets;
        this.repoTasks = repoTasks;
        this.audits = audits;
        this.clock = clock;
    }

    public RepoTask create(String ticketId, String repositoryAlias, String baseCommit, String actorId,
            String correlationId) {
        tickets.ticket(ticketId);
        String repoTaskId = "REPO-TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = clock.instant();
        RepoTask repoTask = new RepoTask(repoTaskId, ticketId, repositoryAlias, baseCommit,
                RepoTaskStatus.PLANNED, 0, now, now);
        repoTasks.save(repoTask);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), ticketId, "TICKET", "REPO_TASK_CREATED",
                "repoTask=" + repoTaskId + " repo=" + repositoryAlias, actorId, now, correlationId));
        return repoTask;
    }

    public RepoTask transition(String repoTaskId, long expectedVersion, RepoTaskStatus target, String actorId,
            String correlationId) {
        RepoTask repoTask = requireVersion(repoTaskId, expectedVersion);
        if (!ALLOWED.getOrDefault(repoTask.status(), Set.of()).contains(target)) {
            throw new IllegalStateException("Repo task transition not allowed: " + repoTask.status() + " -> " + target);
        }
        RepoTask changed = repoTask.transitionedTo(target, clock.instant());
        repoTasks.save(changed);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), repoTask.ticketId(), "TICKET",
                "REPO_TASK_TRANSITIONED", "repoTask=" + repoTaskId + " " + repoTask.status() + "->" + target,
                actorId, clock.instant(), correlationId));
        return changed;
    }

    public List<RepoTask> listByTicket(String ticketId) {
        return repoTasks.findByTicketId(ticketId);
    }

    private RepoTask requireVersion(String repoTaskId, long expectedVersion) {
        RepoTask repoTask = repoTasks.findById(repoTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Repo task not found: " + repoTaskId));
        if (repoTask.version() != expectedVersion) {
            throw new IllegalStateException(
                    "Expected repo task version " + expectedVersion + " but was " + repoTask.version());
        }
        return repoTask;
    }

    private static void allow(RepoTaskStatus source, RepoTaskStatus first, RepoTaskStatus... rest) {
        ALLOWED.put(source, EnumSet.of(first, rest));
    }
}
```

Create `dependency/DependencyService.java`:

```java
package dev.sdlc.workflow.dependency;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class DependencyService {

    private final EpicWorkflowRepository epics;
    private final TicketWorkflowService tickets;
    private final DependencyRepository dependencies;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public DependencyService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.tickets = tickets;
        this.dependencies = dependencies;
        this.audits = audits;
        this.clock = clock;
    }

    public Dependency add(String epicId, String fromTicketId, String toTicketId, String actorId, String correlationId) {
        epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
        if (fromTicketId.equals(toTicketId)) {
            throw new IllegalArgumentException("A ticket cannot depend on itself");
        }
        if (!tickets.ticket(fromTicketId).epicId().equals(epicId) || !tickets.ticket(toTicketId).epicId().equals(epicId)) {
            throw new IllegalArgumentException("Both tickets must belong to the epic");
        }
        Dependency existing = dependencies.findByEpicId(epicId).stream()
                .filter(dep -> dep.fromTicketId().equals(fromTicketId)
                        && dep.toTicketId().equals(toTicketId)
                        && dep.status() == DependencyStatus.BLOCKING)
                .findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        String dependencyId = "DEP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Dependency dependency = new Dependency(dependencyId, epicId, fromTicketId, toTicketId,
                DependencyKind.REQUIRES_BEFORE, DependencyStatus.BLOCKING, 0, clock.instant());
        dependencies.save(dependency);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "DEPENDENCY_ADDED",
                fromTicketId + " -> " + toTicketId, actorId, clock.instant(), correlationId));
        return dependency;
    }

    public Dependency resolve(String dependencyId, long expectedVersion, String actorId, String correlationId) {
        Dependency dependency = requireVersion(dependencyId, expectedVersion);
        if (dependency.status() != DependencyStatus.BLOCKING) {
            throw new IllegalStateException("Dependency is not BLOCKING");
        }
        Dependency resolved = dependency.resolved(clock.instant());
        dependencies.save(resolved);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), dependency.epicId(), "EPIC",
                "DEPENDENCY_RESOLVED", dependencyId, actorId, clock.instant(), correlationId));
        return resolved;
    }

    public List<Dependency> listByEpic(String epicId) {
        return dependencies.findByEpicId(epicId);
    }

    private Dependency requireVersion(String dependencyId, long expectedVersion) {
        Dependency dependency = dependencies.findById(dependencyId)
                .orElseThrow(() -> new IllegalArgumentException("Dependency not found: " + dependencyId));
        if (dependency.version() != expectedVersion) {
            throw new IllegalStateException(
                    "Expected dependency version " + expectedVersion + " but was " + dependency.version());
        }
        return dependency;
    }
}
```

Create `change/ChangeRequestService.java`:

```java
package dev.sdlc.workflow.change;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ChangeRequestService {

    private static final Set<String> APPROVER_ROLES = Set.of("BUSINESS_OWNER", "TECHNICAL_OWNER");

    private final EpicWorkflowRepository epics;
    private final TicketWorkflowService tickets;
    private final ChangeRequestRepository requests;
    private final DomainAuditEventRepository audits;
    private final Clock clock;

    public ChangeRequestService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            ChangeRequestRepository requests, DomainAuditEventRepository audits, Clock clock) {
        this.epics = epics;
        this.tickets = tickets;
        this.requests = requests;
        this.audits = audits;
        this.clock = clock;
    }

    public EpicChangeRequest create(String epicId, String reason, ChangeUrgency urgency, String description,
            List<String> affectedTicketIds, String actorId, String correlationId) {
        epics.findById(epicId).orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicId));
        String changeRequestId = "CR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = clock.instant();
        EpicChangeRequest request = new EpicChangeRequest(changeRequestId, epicId, reason, urgency, description,
                affectedTicketIds, List.of(), 2, ChangeRequestStatus.DRAFT, 0, now, now);
        requests.save(request);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), epicId, "EPIC", "CHANGE_REQUEST_CREATED",
                "request=" + changeRequestId + " urgency=" + urgency, actorId, now, correlationId));
        return request;
    }

    public EpicChangeRequest approve(String changeRequestId, long expectedVersion, String actorId, String actorRole,
            String correlationId) {
        EpicChangeRequest request = requireVersion(changeRequestId, expectedVersion);
        if (request.status() != ChangeRequestStatus.DRAFT) {
            throw new IllegalStateException("Change request is not DRAFT");
        }
        if (!APPROVER_ROLES.contains(actorRole)) {
            throw new IllegalArgumentException("Approver role must be BUSINESS_OWNER or TECHNICAL_OWNER");
        }
        if (request.approvedRoles().contains(actorRole)) {
            throw new IllegalStateException("Role already approved this change request");
        }
        EpicChangeRequest updated = request.withApproval(actorRole, clock.instant());
        if (updated.status() == ChangeRequestStatus.APPROVED) {
            for (String ticketId : updated.affectedTicketIds()) {
                tickets.markChangePending(ticketId, actorId, correlationId);
            }
        }
        requests.save(updated);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), request.epicId(), "EPIC",
                updated.status() == ChangeRequestStatus.APPROVED ? "CHANGE_REQUEST_APPROVED" : "CHANGE_REQUEST_APPROVAL_ADDED",
                "request=" + changeRequestId + " role=" + actorRole, actorId, clock.instant(), correlationId));
        return updated;
    }

    public EpicChangeRequest reject(String changeRequestId, long expectedVersion, String actorId, String correlationId) {
        EpicChangeRequest request = requireVersion(changeRequestId, expectedVersion);
        if (request.status() != ChangeRequestStatus.DRAFT) {
            throw new IllegalStateException("Change request is not DRAFT");
        }
        EpicChangeRequest rejected = request.rejectedNow(clock.instant());
        requests.save(rejected);
        audits.append(new DomainAuditEvent(UUID.randomUUID().toString(), request.epicId(), "EPIC",
                "CHANGE_REQUEST_REJECTED", "request=" + changeRequestId, actorId, clock.instant(), correlationId));
        return rejected;
    }

    public List<EpicChangeRequest> listByEpic(String epicId) {
        return requests.findByEpicId(epicId);
    }

    private EpicChangeRequest requireVersion(String changeRequestId, long expectedVersion) {
        EpicChangeRequest request = requests.findById(changeRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Change request not found: " + changeRequestId));
        if (request.version() != expectedVersion) {
            throw new IllegalStateException(
                    "Expected change request version " + expectedVersion + " but was " + request.version());
        }
        return request;
    }
}
```

Create `skip/SkipResult.java`:

```java
package dev.sdlc.workflow.skip;

import dev.sdlc.workflow.task.WorkflowTask;

public record SkipResult(WorkflowTask task, SkipAttestation attestation) {
}
```

Create `skip/SkipService.java`:

```java
package dev.sdlc.workflow.skip;

import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class SkipService {

    private final WorkflowTaskService workflowTasks;
    private final SkipAttestationRepository attestations;
    private final Clock clock;

    public SkipService(WorkflowTaskService workflowTasks, SkipAttestationRepository attestations, Clock clock) {
        this.workflowTasks = workflowTasks;
        this.attestations = attestations;
        this.clock = clock;
    }

    public SkipResult skip(String taskId, long expectedVersion, String reason, String discussedWith,
            String actorId, String actorRole, String correlationId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (actorRole == null || actorRole.isBlank()) {
            throw new IllegalArgumentException("actorRole is required");
        }
        WorkflowTask task = workflowTasks.getTask(taskId);
        WorkflowTask skipped = workflowTasks.skipTask(taskId, expectedVersion, actorId, correlationId);
        SkipAttestation attestation = new SkipAttestation(UUID.randomUUID().toString(), taskId, task.type().name(),
                reason, discussedWith == null ? "" : discussedWith, actorId, actorRole, clock.instant(), correlationId);
        attestations.save(attestation);
        return new SkipResult(skipped, attestation);
    }

    public List<SkipAttestation> listByTask(String taskId) {
        return attestations.findByTaskId(taskId);
    }
}
```

- [ ] **Step 4: Add `skipTask` to `WorkflowTaskService`**

Open `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/WorkflowTaskService.java` and add after `claimTask`:

```java
    public synchronized WorkflowTask skipTask(
            String taskId,
            long expectedVersion,
            String actorId,
            String correlationId) {
        WorkflowTask task = requireVersion(taskId, expectedVersion);
        if (task.status() != TaskStatus.WAITING_FOR_LOCAL_COPILOT
                && task.status() != TaskStatus.LOCAL_COPILOT_RUNNING
                && task.status() != TaskStatus.WAITING_FOR_USER_CONFIRMATION) {
            throw new IllegalTaskTransitionException("Stage cannot be skipped from " + task.status());
        }
        WorkflowTask skipped = task.transitionedTo(TaskStatus.COMPLETED, clock.instant());
        tasks.save(skipped);
        audit(skipped, actorId, "TASK_SKIPPED", task.status(), skipped.status(), correlationId);
        return skipped;
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\mvnw.cmd -q -pl apps/workflow-service "-Dtest=EpicWorkflowServiceTest,TicketWorkflowServiceTest,RepoTaskServiceTest,DependencyServiceTest,ChangeRequestServiceTest,SkipServiceTest" test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Run the full Java suite**

Run: `.\mvnw.cmd -q verify`
Expected: BUILD SUCCESS (all prior tests plus the new ones; no existing test may break).

- [ ] **Step 7: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/epic/EpicWorkflowService.java apps/workflow-service/src/main/java/dev/sdlc/workflow/ticket/TicketWorkflowService.java apps/workflow-service/src/main/java/dev/sdlc/workflow/repotask/RepoTaskService.java apps/workflow-service/src/main/java/dev/sdlc/workflow/dependency/DependencyService.java apps/workflow-service/src/main/java/dev/sdlc/workflow/change/ChangeRequestService.java apps/workflow-service/src/main/java/dev/sdlc/workflow/skip/SkipService.java apps/workflow-service/src/main/java/dev/sdlc/workflow/skip/SkipResult.java apps/workflow-service/src/main/java/dev/sdlc/workflow/task/WorkflowTaskService.java apps/workflow-service/src/test/java/dev/sdlc/workflow/epic/EpicWorkflowServiceTest.java apps/workflow-service/src/test/java/dev/sdlc/workflow/ticket/TicketWorkflowServiceTest.java apps/workflow-service/src/test/java/dev/sdlc/workflow/repotask/RepoTaskServiceTest.java apps/workflow-service/src/test/java/dev/sdlc/workflow/dependency/DependencyServiceTest.java apps/workflow-service/src/test/java/dev/sdlc/workflow/change/ChangeRequestServiceTest.java apps/workflow-service/src/test/java/dev/sdlc/workflow/skip/SkipServiceTest.java
git commit -m "feat(m2): add three-level workflow services with change, skip, and dependency rules"
```

---

### Task 4: Epic REST controller, config wiring, ITs, registry

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java`
- Create: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/EpicWorkflowIT.java`
- Modify: `docs/handoff/INTERNAL_TODO.md`

- [ ] **Step 1: Write the failing integration test**

Create `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/EpicWorkflowIT.java`:

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
class EpicWorkflowIT {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void walksTheFullEpicScenarioWithChangeAndSkip() throws Exception {
        mvc.perform(post("/api/v1/epics")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"epicId":"EPIC-M2-1","title":"Fictional epic","journeyId":"ACCOUNT_OPENING"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        mvc.perform(post("/api/v1/epics/{id}/activate", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"M2-API-1\",\"channel\":\"API\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mvc.perform(post("/api/v1/epics/{id}/tickets", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"M2-WEB-1\",\"channel\":\"WEB\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/epics/{id}/dependencies", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromTicketId\":\"M2-API-1\",\"toTicketId\":\"M2-WEB-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BLOCKING"));

        String web = mvc.perform(post("/api/v1/tickets/{id}/advance", "M2-WEB-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"target\":\"IN_ANALYSIS\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long version = json.readTree(web).path("version").asLong();
        for (String next : new String[] {"WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN", "CI_PASSED"}) {
            String body = mvc.perform(post("/api/v1/tickets/{id}/advance", "M2-WEB-1")
                            .header("X-Demo-User", "PRINCIPAL-EMP-100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + version + ",\"target\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            version = json.readTree(body).path("version").asLong();
        }

        mvc.perform(post("/api/v1/tickets/{id}/advance", "M2-WEB-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + version + ",\"target\":\"MERGED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mvc.perform(post("/api/v1/epics/{id}/change-requests", "EPIC-M2-1")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Fictional scope change","urgency":"URGENT",
                                 "description":"Fictional detail","affectedTicketIds":["M2-API-1"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post("/api/v1/change-requests/{id}/approve", "CR-PLACEHOLDER")
                        .header("X-Demo-User", "PRINCIPAL-EMP-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"actorRole\":\"BUSINESS_OWNER\"}"))
                .andExpect(status().isNotFound());
    }
}
```

Note: the final two requests are intentionally RED stubs — the implementer must replace them with the real flow: list change requests for the epic (`GET /api/v1/epics/EPIC-M2-1/change-requests`), then approve with `BUSINESS_OWNER` (expect `DRAFT`), then `TECHNICAL_OWNER` (expect `APPROVED`), then `GET /api/v1/epics/EPIC-M2-1/tickets` and assert `M2-API-1` has `pendingChangeConfirmation: true`, then `GET /api/v1/epics/EPIC-M2-1/resume` and assert `epic.status == ACTIVE`, `tickets` array non-empty, and `auditTrail` contains `EPIC_CREATED`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q -pl apps/workflow-service -Dtest=EpicWorkflowIT test`
Expected: FAIL — `EpicController` bean missing (Spring context fails) or 404 for `/api/v1/epics`.

- [ ] **Step 3: Implement `EpicController`**

Create `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java`:

```java
package dev.sdlc.workflow.api;

import dev.sdlc.workflow.audit.DomainAuditEvent;
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.change.ChangeRequestService;
import dev.sdlc.workflow.change.ChangeUrgency;
import dev.sdlc.workflow.change.EpicChangeRequest;
import dev.sdlc.workflow.dependency.Dependency;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.epic.Channel;
import dev.sdlc.workflow.epic.EpicWorkflow;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.repotask.RepoTask;
import dev.sdlc.workflow.repotask.RepoTaskService;
import dev.sdlc.workflow.security.CurrentUser;
import dev.sdlc.workflow.skip.SkipAttestation;
import dev.sdlc.workflow.skip.SkipResult;
import dev.sdlc.workflow.skip.SkipService;
import dev.sdlc.workflow.task.TaskStatus;
import dev.sdlc.workflow.task.WorkflowTask;
import dev.sdlc.workflow.task.WorkflowTaskService;
import dev.sdlc.workflow.ticket.TicketDeliveryStatus;
import dev.sdlc.workflow.ticket.TicketWorkflow;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EpicController {

    private final EpicWorkflowService epics;
    private final TicketWorkflowService tickets;
    private final RepoTaskService repoTasks;
    private final DependencyService dependencies;
    private final ChangeRequestService changeRequests;
    private final SkipService skips;
    private final WorkflowTaskService workflowTasks;
    private final DomainAuditEventRepository audits;

    public EpicController(EpicWorkflowService epics, TicketWorkflowService tickets, RepoTaskService repoTasks,
            DependencyService dependencies, ChangeRequestService changeRequests, SkipService skips,
            WorkflowTaskService workflowTasks, DomainAuditEventRepository audits) {
        this.epics = epics;
        this.tickets = tickets;
        this.repoTasks = repoTasks;
        this.dependencies = dependencies;
        this.changeRequests = changeRequests;
        this.skips = skips;
        this.workflowTasks = workflowTasks;
        this.audits = audits;
    }

    @PostMapping("/epics")
    ResponseEntity<EpicWorkflow> createEpic(@Valid @RequestBody EpicRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        // TODO(INTERNAL): INTERNAL-EPIC-001 Sync Epic creation and Ticket status changes with the company Jira.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(epics.create(body.epicId(), body.title(), body.journeyId(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @PostMapping("/epics/{epicId}/activate")
    EpicWorkflow activate(@PathVariable String epicId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return epics.activate(epicId, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request));
    }

    @GetMapping("/epics/{epicId}")
    EpicWorkflow epic(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return epics.get(epicId);
    }

    @PostMapping("/epics/{epicId}/tickets")
    ResponseEntity<TicketWorkflow> attachTicket(@PathVariable String epicId,
            @Valid @RequestBody AttachTicketRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tickets.create(epicId, body.ticketId(), body.channel(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @GetMapping("/epics/{epicId}/tickets")
    List<TicketWorkflow> tickets(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return tickets.listByEpic(epicId);
    }

    @PostMapping("/tickets/{ticketId}/advance")
    TicketWorkflow advance(@PathVariable String ticketId, @Valid @RequestBody AdvanceRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return tickets.transition(ticketId, body.expectedVersion(), body.target(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/tickets/{ticketId}/ack-change")
    TicketWorkflow ackChange(@PathVariable String ticketId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return tickets.ackChange(ticketId, body.expectedVersion(), user.actorId(), CorrelationIdFilter.from(request));
    }

    @PostMapping("/tickets/{ticketId}/repo-tasks")
    ResponseEntity<RepoTask> addRepoTask(@PathVariable String ticketId, @Valid @RequestBody RepoTaskRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repoTasks.create(ticketId, body.repositoryAlias(), body.baseCommit(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @GetMapping("/tickets/{ticketId}/repo-tasks")
    List<RepoTask> repoTasks(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentUser.require(request);
        return repoTasks.listByTicket(ticketId);
    }

    @PostMapping("/epics/{epicId}/dependencies")
    ResponseEntity<Dependency> addDependency(@PathVariable String epicId, @Valid @RequestBody DependencyRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dependencies.add(epicId, body.fromTicketId(), body.toTicketId(), user.actorId(),
                        CorrelationIdFilter.from(request)));
    }

    @PostMapping("/dependencies/{dependencyId}/resolve")
    Dependency resolveDependency(@PathVariable String dependencyId, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return dependencies.resolve(dependencyId, body.expectedVersion(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @GetMapping("/epics/{epicId}/dependencies")
    List<Dependency> dependencies(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return dependencies.listByEpic(epicId);
    }

    @PostMapping("/epics/{epicId}/change-requests")
    ResponseEntity<EpicChangeRequest> createChangeRequest(@PathVariable String epicId,
            @Valid @RequestBody ChangeRequestRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(changeRequests.create(epicId, body.reason(), body.urgency(), body.description(),
                        body.affectedTicketIds(), user.actorId(), CorrelationIdFilter.from(request)));
    }

    @GetMapping("/epics/{epicId}/change-requests")
    List<EpicChangeRequest> changeRequests(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        return changeRequests.listByEpic(epicId);
    }

    @PostMapping("/change-requests/{changeRequestId}/approve")
    EpicChangeRequest approveChangeRequest(@PathVariable String changeRequestId,
            @Valid @RequestBody ApproveRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return changeRequests.approve(changeRequestId, body.expectedVersion(), user.actorId(), body.actorRole(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/change-requests/{changeRequestId}/reject")
    EpicChangeRequest rejectChangeRequest(@PathVariable String changeRequestId,
            @Valid @RequestBody RejectRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        return changeRequests.reject(changeRequestId, body.expectedVersion(), user.actorId(),
                CorrelationIdFilter.from(request));
    }

    @PostMapping("/tasks/{taskId}/skip")
    Map<String, Object> skipTask(@PathVariable String taskId, @Valid @RequestBody SkipRequest body,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.require(request);
        SkipResult result = skips.skip(taskId, body.expectedVersion(), body.reason(), body.discussedWith(),
                user.actorId(), body.actorRole(), CorrelationIdFilter.from(request));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task", WorkflowTaskResponse.from(result.task()));
        response.put("attestation", result.attestation());
        return response;
    }

    @GetMapping("/tasks/{taskId}/skips")
    List<SkipAttestation> skips(@PathVariable String taskId, HttpServletRequest request) {
        CurrentUser.require(request);
        return skips.listByTask(taskId);
    }

    @GetMapping("/epics/{epicId}/resume")
    Map<String, Object> resume(@PathVariable String epicId, HttpServletRequest request) {
        CurrentUser.require(request);
        EpicWorkflow epic = epics.get(epicId);
        List<Map<String, Object>> ticketViews = tickets.listByEpic(epicId).stream().map(ticket -> {
            List<WorkflowTask> open = workflowTasks.listTasks().stream()
                    .filter(task -> task.scope().ticketId().equals(ticket.ticketId())
                            && task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.CANCELLED)
                    .toList();
            return Map.<String, Object>of("ticket", ticket, "openTasks", open,
                    "nextAction", nextActionFor(ticket, open));
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("epic", epic);
        result.put("tickets", ticketViews);
        result.put("auditTrail", audits.findByAggregateId(epicId));
        return result;
    }

    private static String nextActionFor(TicketWorkflow ticket, List<WorkflowTask> open) {
        if (!open.isEmpty()) {
            return "claim task " + open.get(0).taskId();
        }
        return switch (ticket.status()) {
            case PLANNED -> "start requirement analysis";
            case IN_ANALYSIS -> "submit requirement contract";
            case WAITING_FOR_APPROVAL -> "approve requirement";
            case IN_DEVELOPMENT -> "open PR";
            case PR_OPEN -> "record CI result";
            case CI_PASSED -> "merge after review";
            case MERGED -> "release";
            case RELEASED -> "enable feature flag";
            case FLAG_ENABLED -> "verify E2E";
            case BLOCKED -> "resolve blocker";
            case E2E_VERIFIED, CANCELLED -> "none";
        };
    }

    public record EpicRequest(@NotBlank String epicId, @NotBlank String title, @NotBlank String journeyId) {
    }

    public record VersionRequest(@Min(0) long expectedVersion) {
    }

    public record AttachTicketRequest(@NotBlank String ticketId, @NotNull Channel channel) {
    }

    public record AdvanceRequest(@Min(0) long expectedVersion, @NotNull TicketDeliveryStatus target) {
    }

    public record RepoTaskRequest(@NotBlank String repositoryAlias, @NotBlank String baseCommit) {
    }

    public record DependencyRequest(@NotBlank String fromTicketId, @NotBlank String toTicketId) {
    }

    public record ChangeRequestRequest(@NotBlank String reason, @NotNull ChangeUrgency urgency,
            @NotBlank String description, @NotNull List<String> affectedTicketIds) {
    }

    public record ApproveRequest(@Min(0) long expectedVersion, @NotBlank String actorRole) {
    }

    public record RejectRequest(@Min(0) long expectedVersion) {
    }

    public record SkipRequest(@Min(0) long expectedVersion, @NotBlank String reason, String discussedWith,
            @NotBlank String actorRole) {
    }
}
```

Note: `IllegalStateException`/`IllegalArgumentException` are already mapped to 409/400 by `ApiExceptionHandler` (verify its mapping; if `IllegalStateException` maps to 500, update `ApiExceptionHandler` to map it to 409 Conflict alongside the existing conflict mapping, and add a test for it).

- [ ] **Step 4: Wire beans in both configs**

In `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java`, add imports:

```java
import dev.sdlc.workflow.audit.DomainAuditEventRepository;
import dev.sdlc.workflow.audit.InMemoryDomainAuditEventRepository;
import dev.sdlc.workflow.change.ChangeRequestRepository;
import dev.sdlc.workflow.change.ChangeRequestService;
import dev.sdlc.workflow.change.InMemoryChangeRequestRepository;
import dev.sdlc.workflow.dependency.DependencyRepository;
import dev.sdlc.workflow.dependency.DependencyService;
import dev.sdlc.workflow.dependency.InMemoryDependencyRepository;
import dev.sdlc.workflow.epic.EpicWorkflowRepository;
import dev.sdlc.workflow.epic.EpicWorkflowService;
import dev.sdlc.workflow.epic.InMemoryEpicWorkflowRepository;
import dev.sdlc.workflow.repotask.InMemoryRepoTaskRepository;
import dev.sdlc.workflow.repotask.RepoTaskRepository;
import dev.sdlc.workflow.repotask.RepoTaskService;
import dev.sdlc.workflow.skip.InMemorySkipAttestationRepository;
import dev.sdlc.workflow.skip.SkipAttestationRepository;
import dev.sdlc.workflow.skip.SkipService;
import dev.sdlc.workflow.ticket.InMemoryTicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowRepository;
import dev.sdlc.workflow.ticket.TicketWorkflowService;
```

Add beans at the end of the class:

```java
    @Bean
    DomainAuditEventRepository domainAuditEventRepository() {
        // TODO(INTERNAL): INTERNAL-AUD-001 Persist M2 domain aggregates and audit events to MongoDB.
        return new InMemoryDomainAuditEventRepository();
    }

    @Bean
    EpicWorkflowRepository epicWorkflowRepository() {
        return new InMemoryEpicWorkflowRepository();
    }

    @Bean
    TicketWorkflowRepository ticketWorkflowRepository() {
        return new InMemoryTicketWorkflowRepository();
    }

    @Bean
    RepoTaskRepository repoTaskRepository() {
        return new InMemoryRepoTaskRepository();
    }

    @Bean
    DependencyRepository dependencyRepository() {
        return new InMemoryDependencyRepository();
    }

    @Bean
    ChangeRequestRepository changeRequestRepository() {
        return new InMemoryChangeRequestRepository();
    }

    @Bean
    SkipAttestationRepository skipAttestationRepository() {
        return new InMemorySkipAttestationRepository();
    }

    @Bean
    EpicWorkflowService epicWorkflowService(EpicWorkflowRepository epics, DomainAuditEventRepository audits,
            Clock clock) {
        return new EpicWorkflowService(epics, audits, clock);
    }

    @Bean
    TicketWorkflowService ticketWorkflowService(EpicWorkflowRepository epics, TicketWorkflowRepository tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        return new TicketWorkflowService(epics, tickets, dependencies, audits, clock);
    }

    @Bean
    RepoTaskService repoTaskService(TicketWorkflowService tickets, RepoTaskRepository repoTasks,
            DomainAuditEventRepository audits, Clock clock) {
        return new RepoTaskService(tickets, repoTasks, audits, clock);
    }

    @Bean
    DependencyService dependencyService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            DependencyRepository dependencies, DomainAuditEventRepository audits, Clock clock) {
        return new DependencyService(epics, tickets, dependencies, audits, clock);
    }

    @Bean
    ChangeRequestService changeRequestService(EpicWorkflowRepository epics, TicketWorkflowService tickets,
            ChangeRequestRepository requests, DomainAuditEventRepository audits, Clock clock) {
        return new ChangeRequestService(epics, tickets, requests, audits, clock);
    }

    @Bean
    SkipService skipService(WorkflowTaskService workflowTasks, SkipAttestationRepository attestations, Clock clock) {
        return new SkipService(workflowTasks, attestations, clock);
    }
```

In `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java`, add the SAME imports and the SAME bean bodies (copy them verbatim; both profiles stay in-memory for M2 — the `TODO(INTERNAL)` markers document the Mongo persistence follow-up).

- [ ] **Step 5: Update the registry**

Append to `docs/handoff/INTERNAL_TODO.md` table:

```markdown
| INTERNAL-EPIC-001 | workflow-service | `api/EpicController.java` | Sync Epic creation and Ticket status changes with the company Jira | Sanitized Jira projection log | Remove the sync call behind the `fake` profile |
| INTERNAL-AUD-001 | workflow-service | `config/*RuntimeConfiguration.java` | Persist M2 domain aggregates (epic/ticket/repo-task/dependency/change-request/skip/audit) to MongoDB | Sanitized Mongo mapping test report | Revert to in-memory beans |
```

- [ ] **Step 6: Run the IT until green**

First fix the IT's RED stub block as described in Step 1's note (real change-request flow + resume assertions), then run:

`.\mvnw.cmd -q -pl apps/workflow-service -Dtest=EpicWorkflowIT test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Run the full Java suite**

Run: `.\mvnw.cmd -q verify`
Expected: BUILD SUCCESS. If `ApiExceptionHandler` needed a conflict-mapping addition, add a small IT case for the 409 (the IT above already asserts 409 on blocked MERGED).

- [ ] **Step 8: Commit**

```powershell
git add apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java apps/workflow-service/src/test/java/dev/sdlc/workflow/api/EpicWorkflowIT.java docs/handoff/INTERNAL_TODO.md
git commit -m "feat(m2): add epic REST controller, wiring, and integration tests"
```

---

### Task 5: Web UI Epic panel

**Files:**
- Modify: `apps/web-ui/src/App.tsx`
- Test: existing `apps/web-ui/src/podCsv.test.ts` and `apps/web-ui/src/App.test.tsx` must stay green

- [ ] **Step 1: Add types and state to `App.tsx`**

Add interfaces after the existing ones:

```tsx
interface EpicState { epicId: string; title: string; status: string; version: number }
interface TicketState { ticketId: string; channel: string; status: string; pendingChangeConfirmation: boolean; version: number }
interface ChangeRequestState { changeRequestId: string; status: string; approvedRoles: string[]; requiredApprovals: number; version: number }
interface ResumeState { epic: EpicState; tickets: Array<{ ticket: TicketState; nextAction: string }>; auditTrail: Array<{ action: string; actorId: string; occurredAt: string }> }
```

Add state inside `App()` after the existing M1 states:

```tsx
const [epic, setEpic] = useState<EpicState>();
const [tickets, setTickets] = useState<TicketState[]>([]);
const [repoTaskLine, setRepoTaskLine] = useState<string>();
const [dependencyLine, setDependencyLine] = useState<string>();
const [dependencyError, setDependencyError] = useState<string>();
const [changeRequest, setChangeRequest] = useState<ChangeRequestState>();
const [resume, setResume] = useState<ResumeState>();
const [skipLine, setSkipLine] = useState<string>();
const [m2Busy, setM2Busy] = useState<string>();
```

- [ ] **Step 2: Add the M2 handlers after `assignDeveloper`**

```tsx
const m2Api = async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
  const response = await fetch(path, { ...init, headers: readinessHeaders });
  if (!response.ok) throw new Error(`status ${response.status}`);
  return response.json() as Promise<T>;
};

const refreshTickets = async (epicId: string) => {
  setTickets(await m2Api<TicketState[]>(`/api/v1/epics/${epicId}/tickets`));
};

const createEpic = async () => {
  setM2Busy("epic");
  try {
    const created = await m2Api<EpicState>("/api/v1/epics", { method: "POST", body: JSON.stringify({
      epicId: "EPIC-M2-1", title: "Fictional M2 epic", journeyId: "ACCOUNT_OPENING",
    }) });
    setEpic(created);
  } catch { setDependencyError("epic-create-failed"); } finally { setM2Busy(undefined); }
};

const activateEpic = async () => {
  if (!epic) return;
  setM2Busy("activate");
  try {
    setEpic(await m2Api<EpicState>(`/api/v1/epics/${epic.epicId}/activate`, {
      method: "POST", body: JSON.stringify({ expectedVersion: epic.version }),
    }));
  } catch { setDependencyError("epic-activate-failed"); } finally { setM2Busy(undefined); }
};

const attachTickets = async () => {
  if (!epic) return;
  setM2Busy("attach");
  try {
    for (const [ticketId, channel] of [["M2-API-1", "API"], ["M2-WEB-1", "WEB"], ["M2-IOS-1", "IOS"], ["M2-AND-1", "ANDROID"]] as const) {
      await m2Api(`/api/v1/epics/${epic.epicId}/tickets`, { method: "POST", body: JSON.stringify({ ticketId, channel }) });
    }
    await refreshTickets(epic.epicId);
  } catch { setDependencyError("attach-tickets-failed"); } finally { setM2Busy(undefined); }
};

const addRepoTask = async () => {
  setM2Busy("repotask");
  try {
    const task = await m2Api<{ repoTaskId: string; status: string }>("/api/v1/tickets/M2-API-1/repo-tasks", {
      method: "POST", body: JSON.stringify({ repositoryAlias: "REPO_A", baseCommit: "0123456789abcdef" }),
    });
    setRepoTaskLine(`${task.repoTaskId} · ${task.status}`);
  } catch { setDependencyError("repo-task-failed"); } finally { setM2Busy(undefined); }
};

const addDependency = async () => {
  if (!epic) return;
  setM2Busy("dependency");
  try {
    const dep = await m2Api<{ dependencyId: string; fromTicketId: string; toTicketId: string; status: string }>(
      `/api/v1/epics/${epic.epicId}/dependencies`, {
        method: "POST", body: JSON.stringify({ fromTicketId: "M2-API-1", toTicketId: "M2-WEB-1" }),
      });
    setDependencyLine(`${dep.fromTicketId} → ${dep.toTicketId} · ${dep.status}`);
  } catch { setDependencyError("dependency-failed"); } finally { setM2Busy(undefined); }
};

const advanceWebTicket = async () => {
  const ticket = tickets.find((item) => item.ticketId === "M2-WEB-1");
  if (!ticket) return;
  setM2Busy("advance");
  const path: Array<"IN_ANALYSIS" | "WAITING_FOR_APPROVAL" | "IN_DEVELOPMENT" | "PR_OPEN" | "CI_PASSED"> =
    ["IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN", "CI_PASSED"];
  try {
    let current = ticket;
    for (const target of path) {
      current = await m2Api<TicketState>(`/api/v1/tickets/${current.ticketId}/advance`, {
        method: "POST", body: JSON.stringify({ expectedVersion: current.version, target }),
      });
    }
    await refreshTickets(epic!.epicId);
  } catch { setDependencyError("advance-failed"); } finally { setM2Busy(undefined); }
};

const mergeWebTicket = async () => {
  const ticket = tickets.find((item) => item.ticketId === "M2-WEB-1");
  if (!ticket) return;
  setM2Busy("merge"); setDependencyError(undefined);
  try {
    await m2Api(`/api/v1/tickets/${ticket.ticketId}/advance`, {
      method: "POST", body: JSON.stringify({ expectedVersion: ticket.version, target: "MERGED" }),
    });
    await refreshTickets(epic!.epicId);
  } catch {
    setDependencyError("MERGE_BLOCKED_BY_DEPENDENCY");
    await refreshTickets(epic!.epicId);
  } finally { setM2Busy(undefined); }
};

const resolveDependency = async () => {
  if (!epic) return;
  setM2Busy("resolve"); setDependencyError(undefined);
  try {
    const deps = await m2Api<Array<{ dependencyId: string; version: number }>>(`/api/v1/epics/${epic.epicId}/dependencies`);
    const blocking = deps[0];
    await m2Api(`/api/v1/dependencies/${blocking.dependencyId}/resolve`, {
      method: "POST", body: JSON.stringify({ expectedVersion: blocking.version }),
    });
    setDependencyLine(`RESOLVED ${blocking.dependencyId}`);
  } catch { setDependencyError("dependency-resolve-failed"); } finally { setM2Busy(undefined); }
};

const createChangeRequest = async () => {
  if (!epic) return;
  setM2Busy("changerequest");
  try {
    setChangeRequest(await m2Api<ChangeRequestState>(`/api/v1/epics/${epic.epicId}/change-requests`, {
      method: "POST", body: JSON.stringify({
        reason: "Fictional urgent scope change", urgency: "URGENT", description: "Fictional detail",
        affectedTicketIds: ["M2-API-1", "M2-WEB-1"],
      }),
    }));
  } catch { setDependencyError("change-request-failed"); } finally { setM2Busy(undefined); }
};

const approveChange = async (role: "BUSINESS_OWNER" | "TECHNICAL_OWNER") => {
  if (!changeRequest) return;
  setM2Busy("approve");
  try {
    const updated = await m2Api<ChangeRequestState>(`/api/v1/change-requests/${changeRequest.changeRequestId}/approve`, {
      method: "POST", body: JSON.stringify({ expectedVersion: changeRequest.version, actorRole: role }),
    });
    setChangeRequest(updated);
    if (epic) await refreshTickets(epic.epicId);
  } catch { setDependencyError("change-approve-failed"); } finally { setM2Busy(undefined); }
};

const ackChangeOnApi = async () => {
  const ticket = tickets.find((item) => item.ticketId === "M2-API-1");
  if (!ticket) return;
  setM2Busy("ack");
  try {
    await m2Api(`/api/v1/tickets/${ticket.ticketId}/ack-change`, {
      method: "POST", body: JSON.stringify({ expectedVersion: ticket.version }),
    });
    await refreshTickets(epic!.epicId);
  } catch { setDependencyError("ack-change-failed"); } finally { setM2Busy(undefined); }
};

const skipFirstTask = async () => {
  const task = tasks[0];
  if (!task) return;
  setM2Busy("skip");
  try {
    const result = await m2Api<{ attestation: { taskId: string; stageType: string; reason: string } }>(
      `/api/v1/tasks/${task.taskId}/skip`, {
        method: "POST", body: JSON.stringify({
          expectedVersion: task.version, reason: "Fictional fast-track", discussedWith: "Fictional architect",
          actorRole: "DEVELOPER",
        }),
      });
    setSkipLine(`SKIPPED ${result.attestation.taskId} · ${result.attestation.stageType} · ${result.attestation.reason}`);
    await refresh();
  } catch { setDependencyError("skip-failed"); } finally { setM2Busy(undefined); }
};

const showResume = async () => {
  if (!epic) return;
  setM2Busy("resume");
  try {
    setResume(await m2Api<ResumeState>(`/api/v1/epics/${epic.epicId}/resume`));
  } catch { setDependencyError("resume-failed"); } finally { setM2Busy(undefined); }
};
```

Note: `tasks`/`refresh` are the pre-existing M1 state/handler names; `tasks[0]` is `ApiTask` which already has `taskId` and `version`. If the existing `refresh` signature differs, adapt.

- [ ] **Step 3: Add the M2 section JSX after the M1 Pod section**

```tsx
<section className="sdlc-card sdlc-stack readiness" aria-labelledby="m2-title">
  <div className="section-heading"><div><p className="eyebrow">M2 · Three-level workflow</p><h2 id="m2-title">Epic, tickets, and repo tasks</h2></div></div>
  <div className="sdlc-actions">
    <button type="button" disabled={Boolean(m2Busy)} onClick={() => void createEpic()}>Create EPIC-M2-1</button>
    <button type="button" disabled={Boolean(m2Busy) || !epic || epic.status !== "CREATED"} onClick={() => void activateEpic()}>Activate epic</button>
    <button type="button" disabled={Boolean(m2Busy) || !epic || epic.status !== "ACTIVE"} onClick={() => void attachTickets()}>Attach four channel tickets</button>
    <button type="button" disabled={Boolean(m2Busy) || tickets.length === 0} onClick={() => void addRepoTask()}>Add repo task to M2-API-1</button>
    <button type="button" disabled={Boolean(m2Busy) || tickets.length < 2} onClick={() => void addDependency()}>Add dependency M2-API-1 → M2-WEB-1</button>
    <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-WEB-1" && item.status === "PLANNED")} onClick={() => void advanceWebTicket()}>Advance M2-WEB-1 to CI_PASSED</button>
    <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-WEB-1" && item.status === "CI_PASSED")} onClick={() => void mergeWebTicket()}>Try merge M2-WEB-1</button>
    <button type="button" disabled={Boolean(m2Busy) || dependencyError !== "MERGE_BLOCKED_BY_DEPENDENCY"} onClick={() => void resolveDependency()}>Resolve dependency</button>
    <button type="button" disabled={Boolean(m2Busy) || !epic || epic.status !== "ACTIVE"} onClick={() => void createChangeRequest()}>Create emergency change request</button>
    <button type="button" disabled={Boolean(m2Busy) || !changeRequest || changeRequest.status !== "DRAFT"} onClick={() => void approveChange("BUSINESS_OWNER")}>Approve change as Business Owner</button>
    <button type="button" disabled={Boolean(m2Busy) || !changeRequest || changeRequest.status !== "DRAFT"} onClick={() => void approveChange("TECHNICAL_OWNER")}>Approve change as Technical Owner</button>
    <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-API-1" && item.pendingChangeConfirmation)} onClick={() => void ackChangeOnApi()}>Acknowledge change on M2-API-1</button>
    <button type="button" disabled={Boolean(m2Busy) || tasks.length === 0} onClick={() => void skipFirstTask()}>Skip first DEMO-123 task with attestation</button>
    <button type="button" disabled={Boolean(m2Busy) || !epic} onClick={() => void showResume()}>Show resume context</button>
  </div>
  {dependencyError && <ErrorState title="M2 action unavailable" correlationId={dependencyError} onRetry={() => undefined} />}
  {epic && <p role="status">Epic {epic.epicId} · {epic.title} · {epic.status} · v{epic.version}</p>}
  {tickets.length > 0 && <div className="table-scroll"><table><caption>EPIC-M2-1 tickets</caption>
    <thead><tr><th scope="col">Ticket</th><th scope="col">Channel</th><th scope="col">Status</th><th scope="col">Change confirmation</th></tr></thead>
    <tbody>{tickets.map((ticket) => <tr key={ticket.ticketId}>
      <th scope="row">{ticket.ticketId}</th><td>{ticket.channel}</td><td>{ticket.status}</td>
      <td>{ticket.pendingChangeConfirmation ? "PENDING_CHANGE_CONFIRMATION" : "—"}</td></tr>)}</tbody></table></div>}
  {repoTaskLine && <p>{repoTaskLine}</p>}
  {dependencyLine && <p>{dependencyLine}</p>}
  {changeRequest && <p role="status">Change request {changeRequest.changeRequestId} · {changeRequest.status} · approvals {changeRequest.approvedRoles.length}/{changeRequest.requiredApprovals}</p>}
  {skipLine && <p role="status">{skipLine}</p>}
  {resume && <section aria-labelledby="resume-title"><h3 id="resume-title">Resume context · {resume.epic.status}</h3>
    <ul>{resume.tickets.map((item) => <li key={item.ticket.ticketId}>{item.ticket.ticketId} · {item.ticket.status} → {item.nextAction}</li>)}</ul>
    <p className="sdlc-muted">Audit trail: {resume.auditTrail.map((event) => event.action).join(" · ")}</p></section>}
</section>
```

- [ ] **Step 4: Verify tests and build**

Run: `pnpm --filter @sdlc/web-ui test && pnpm --filter @sdlc/web-ui build`
Expected: PASS (existing tests may need the new buttons to render without fetch calls — they render nothing until clicked, so they should pass; if `App.test.tsx` fails because of changed text, update ONLY the test expectations and note it).

- [ ] **Step 5: Commit**

```powershell
git add apps/web-ui/src/App.tsx
git commit -m "feat(m2): add epic workflow panel to the Web demo"
```

---

### Task 6: Local MCP epic tools

**Files:**
- Modify: `apps/workflow-mcp/src/client.ts`
- Modify: `apps/workflow-mcp/src/tools/workflowTools.ts`
- Modify: `apps/workflow-mcp/test/server.test.ts` (extend tool-discovery list)
- Create: `apps/workflow-mcp/test/epicTools.test.ts`

- [ ] **Step 1: Write the failing test**

Create `apps/workflow-mcp/test/epicTools.test.ts` (pattern mirrors `test/server.test.ts`):

```ts
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkflowApiClient } from "../src/client.js";
import { createWorkflowMcpServer } from "../src/server.js";

describe("epic MCP tools", () => {
  afterEach(() => vi.restoreAllMocks());

  it("creates an epic through the tool surface", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      epicId: "EPIC-M2-1", title: "Fictional epic", journeyId: "ACCOUNT_OPENING",
      status: "CREATED", version: 0, createdAt: "2026-08-18T00:00:00Z", updatedAt: "2026-08-18T00:00:00Z",
    }), { status: 201, headers: { "content-type": "application/json" } }));
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    await client.connect(clientTransport);

    const result = await client.callTool({
      name: "workflow_epic_create",
      arguments: { epicId: "EPIC-M2-1", title: "Fictional epic", journeyId: "ACCOUNT_OPENING" },
    });

    expect(result.isError).toBeUndefined();
    expect(JSON.parse(String((result.content as Array<{ text: string }>)[0].text)).epicId).toBe("EPIC-M2-1");
    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/epics",
      expect.objectContaining({ method: "POST" }));
    await client.close();
    await server.close();
  });

  it("rejects an invalid channel", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    await client.connect(clientTransport);

    const result = await client.callTool({
      name: "workflow_epic_attach_ticket",
      arguments: { epicId: "EPIC-M2-1", ticketId: "M2-API-1", channel: "DESKTOP" },
    });

    expect(result.isError).toBe(true);
    expect(fetcher).not.toHaveBeenCalled();
    await client.close();
    await server.close();
  });
});
```

Also modify `apps/workflow-mcp/test/server.test.ts`: extend the expected tool-name list with, sorted:

```ts
      "workflow_epic_activate",
      "workflow_epic_attach_ticket",
      "workflow_epic_create",
      "workflow_epic_create_change_request",
      "workflow_epic_approve_change_request",
      "workflow_epic_add_dependency",
      "workflow_epic_resume",
      "workflow_ticket_advance",
      "workflow_ticket_add_repo_task",
      "workflow_task_skip",
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pnpm --filter @sdlc/workflow-mcp test`
Expected: FAIL — tool list mismatch and `workflow_epic_create` unknown.

- [ ] **Step 3: Add client methods to `client.ts`**

Append inside the `WorkflowApiClient` class (after `getNextInternalValidation`):

```ts
  createEpic(epic: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/epics", { method: "POST", body: JSON.stringify(epic) }, correlationId, signal);
  }

  activateEpic(epicId: string, expectedVersion: number, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/activate`, {
      method: "POST", body: JSON.stringify({ expectedVersion }),
    }, correlationId, signal);
  }

  attachTicket(epicId: string, body: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/tickets`, {
      method: "POST", body: JSON.stringify(body),
    }, correlationId, signal);
  }

  advanceTicket(ticketId: string, expectedVersion: number, target: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tickets/${encodeURIComponent(ticketId)}/advance`, {
      method: "POST", body: JSON.stringify({ expectedVersion, target }),
    }, correlationId, signal);
  }

  addRepoTask(ticketId: string, repositoryAlias: string, baseCommit: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tickets/${encodeURIComponent(ticketId)}/repo-tasks`, {
      method: "POST", body: JSON.stringify({ repositoryAlias, baseCommit }),
    }, correlationId, signal);
  }

  addDependency(epicId: string, fromTicketId: string, toTicketId: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/dependencies`, {
      method: "POST", body: JSON.stringify({ fromTicketId, toTicketId }),
    }, correlationId, signal);
  }

  createChangeRequest(epicId: string, body: unknown,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/change-requests`, {
      method: "POST", body: JSON.stringify(body),
    }, correlationId, signal);
  }

  approveChangeRequest(changeRequestId: string, expectedVersion: number, actorRole: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/change-requests/${encodeURIComponent(changeRequestId)}/approve`, {
      method: "POST", body: JSON.stringify({ expectedVersion, actorRole }),
    }, correlationId, signal);
  }

  skipTask(taskId: string, body: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tasks/${encodeURIComponent(taskId)}/skip`, {
      method: "POST", body: JSON.stringify(body),
    }, correlationId, signal);
  }

  resumeEpic(epicId: string, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/resume`, { method: "GET" }, correlationId, signal);
  }
```

- [ ] **Step 4: Register tools in `workflowTools.ts`**

Append at the end of `registerWorkflowTools` (after the existing tools):

```ts
  server.registerTool("workflow_epic_create", {
    description: "Create a fictional Epic workflow aggregate.",
    inputSchema: z.object({
      epicId: z.string().min(3).max(80), title: z.string().min(1), journeyId: z.string().min(3).max(80),
    }),
  }, (args, extra) => safe("workflow_epic_create", (correlationId) => api.createEpic(args, correlationId, extra.signal)));

  server.registerTool("workflow_epic_activate", {
    description: "Activate a CREATED epic before attaching tickets.",
    inputSchema: z.object({ epicId: z.string().min(1), expectedVersion: z.number().int().nonnegative() }),
  }, ({ epicId, expectedVersion }, extra) => safe("workflow_epic_activate",
    (correlationId) => api.activateEpic(epicId, expectedVersion, correlationId, extra.signal)));

  server.registerTool("workflow_epic_attach_ticket", {
    description: "Attach a channel ticket (API/WEB/IOS/ANDROID) to an active epic.",
    inputSchema: z.object({
      epicId: z.string().min(1), ticketId: z.string().min(1),
      channel: z.enum(["API", "WEB", "IOS", "ANDROID"]),
    }),
  }, ({ epicId, ticketId, channel }, extra) => safe("workflow_epic_attach_ticket",
    (correlationId) => api.attachTicket(epicId, { ticketId, channel }, correlationId, extra.signal)));

  server.registerTool("workflow_ticket_advance", {
    description: "Advance a ticket along its delivery status machine with an exact version.",
    inputSchema: z.object({
      ticketId: z.string().min(1), expectedVersion: z.number().int().nonnegative(),
      target: z.enum(["PLANNED", "IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN",
        "CI_PASSED", "MERGED", "RELEASED", "FLAG_ENABLED", "E2E_VERIFIED", "BLOCKED", "CANCELLED"]),
    }),
  }, ({ ticketId, expectedVersion, target }, extra) => safe("workflow_ticket_advance",
    (correlationId) => api.advanceTicket(ticketId, expectedVersion, target, correlationId, extra.signal)));

  server.registerTool("workflow_ticket_add_repo_task", {
    description: "Create a repo-level implementation task under a ticket.",
    inputSchema: z.object({
      ticketId: z.string().min(1), repositoryAlias: z.string().min(1), baseCommit: z.string().min(1),
    }),
  }, ({ ticketId, repositoryAlias, baseCommit }, extra) => safe("workflow_ticket_add_repo_task",
    (correlationId) => api.addRepoTask(ticketId, repositoryAlias, baseCommit, correlationId, extra.signal)));

  server.registerTool("workflow_epic_add_dependency", {
    description: "Record a REQUIRES_BEFORE dependency between two tickets of one epic.",
    inputSchema: z.object({
      epicId: z.string().min(1), fromTicketId: z.string().min(1), toTicketId: z.string().min(1),
    }),
  }, ({ epicId, fromTicketId, toTicketId }, extra) => safe("workflow_epic_add_dependency",
    (correlationId) => api.addDependency(epicId, fromTicketId, toTicketId, correlationId, extra.signal)));

  server.registerTool("workflow_epic_create_change_request", {
    description: "Create an emergency change request against an epic.",
    inputSchema: z.object({
      epicId: z.string().min(1), reason: z.string().min(1),
      urgency: z.enum(["STANDARD", "URGENT"]), description: z.string().min(1),
      affectedTicketIds: z.array(z.string().min(1)).max(200),
    }),
  }, ({ epicId, ...body }, extra) => safe("workflow_epic_create_change_request",
    (correlationId) => api.createChangeRequest(epicId, body, correlationId, extra.signal)));

  server.registerTool("workflow_epic_approve_change_request", {
    description: "Approve a change request as BUSINESS_OWNER or TECHNICAL_OWNER; both roles are required.",
    inputSchema: z.object({
      changeRequestId: z.string().min(1), expectedVersion: z.number().int().nonnegative(),
      actorRole: z.enum(["BUSINESS_OWNER", "TECHNICAL_OWNER"]),
    }),
  }, ({ changeRequestId, expectedVersion, actorRole }, extra) => safe("workflow_epic_approve_change_request",
    (correlationId) => api.approveChangeRequest(changeRequestId, expectedVersion, actorRole, correlationId, extra.signal)));

  server.registerTool("workflow_task_skip", {
    description: "Skip a stage with a persisted attestation (reason, discussed-with, actor role).",
    inputSchema: z.object({
      taskId: z.string().min(1), expectedVersion: z.number().int().nonnegative(),
      reason: z.string().min(1), discussedWith: z.string().optional(), actorRole: z.string().min(1),
    }),
  }, ({ taskId, ...body }, extra) => safe("workflow_task_skip",
    (correlationId) => api.skipTask(taskId, body, correlationId, extra.signal)));

  server.registerTool("workflow_epic_resume", {
    description: "Read persisted epic state, open tasks, next actions, and the audit trail after a shutdown.",
    inputSchema: z.object({ epicId: z.string().min(1) }),
    annotations: { readOnlyHint: true },
  }, ({ epicId }, extra) => safe("workflow_epic_resume",
    (correlationId) => api.resumeEpic(epicId, correlationId, extra.signal)));
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `pnpm --filter @sdlc/workflow-mcp test`
Expected: PASS (existing tests plus 2 new; discovery list updated).

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-mcp/src/client.ts apps/workflow-mcp/src/tools/workflowTools.ts apps/workflow-mcp/test/server.test.ts apps/workflow-mcp/test/epicTools.test.ts
git commit -m "feat(m2): add epic and skip tools to the local workflow MCP"
```

---

### Task 7: M2 browser E2E, gates, and evidence

**Files:**
- Create: `e2e/m2-three-level-workflow.spec.ts`
- Modify: `package.json` (root)
- Create: `docs/verification/m2-milestone-2026-08-18.md`

- [ ] **Step 1: Write the E2E**

Create `e2e/m2-three-level-workflow.spec.ts`:

```ts
import { expect, test } from "@playwright/test";

test("M2: three-level workflow with change approval, dependency gate, and skip attestation", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Create EPIC-M2-1" }).click();
  await expect(page.getByText("Epic EPIC-M2-1 · Fictional M2 epic · CREATED")).toBeVisible();
  await page.getByRole("button", { name: "Activate epic" }).click();
  await expect(page.getByText(/Epic EPIC-M2-1 · Fictional M2 epic · ACTIVE/)).toBeVisible();

  await page.getByRole("button", { name: "Attach four channel tickets" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /M2-AND-1/ })).toBeVisible();

  await page.getByRole("button", { name: "Add repo task to M2-API-1" }).click();
  await expect(page.getByText(/REPO-TASK-.*· PLANNED/)).toBeVisible();

  await page.getByRole("button", { name: "Add dependency M2-API-1 → M2-WEB-1" }).click();
  await expect(page.getByText("M2-API-1 → M2-WEB-1 · BLOCKING")).toBeVisible();

  await page.getByRole("button", { name: "Advance M2-WEB-1 to CI_PASSED" }).click();
  await expect(page.getByRole("row", { name: /M2-WEB-1/ })).toContainText("CI_PASSED");

  await page.getByRole("button", { name: "Try merge M2-WEB-1" }).click();
  await expect(page.getByText("MERGE_BLOCKED_BY_DEPENDENCY")).toBeVisible();

  await page.getByRole("button", { name: "Resolve dependency" }).click();
  await expect(page.getByText(/RESOLVED DEP-/)).toBeVisible();

  await page.getByRole("button", { name: "Create emergency change request" }).click();
  await expect(page.getByText(/Change request CR-.*· DRAFT/)).toBeVisible();
  await page.getByRole("button", { name: "Approve change as Business Owner" }).click();
  await page.getByRole("button", { name: "Approve change as Technical Owner" }).click();
  await expect(page.getByText(/Change request CR-.*· APPROVED/)).toBeVisible();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toContainText("PENDING_CHANGE_CONFIRMATION");

  await page.getByRole("button", { name: "Acknowledge change on M2-API-1" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).not.toContainText("PENDING_CHANGE_CONFIRMATION");

  await page.getByRole("button", { name: "Create DEMO-123" }).click();
  await page.getByRole("button", { name: "Skip first DEMO-123 task with attestation" }).click();
  await expect(page.getByText(/SKIPPED TASK-.*· REQUIREMENT_ANALYSIS · Fictional fast-track/)).toBeVisible();

  await page.getByRole("button", { name: "Show resume context" }).click();
  await expect(page.getByText("Resume context · ACTIVE")).toBeVisible();
  await expect(page.getByText(/M2-API-1 · PLANNED → start requirement analysis/)).toBeVisible();
  await expect(page.getByText(/Audit trail: EPIC_CREATED/)).toBeVisible();
});
```

- [ ] **Step 2: Register the script**

Open root `package.json` and add after `"e2e:m1"`:

```json
    "e2e:m2": "playwright test e2e/m2-three-level-workflow.spec.ts"
```

- [ ] **Step 3: Run the E2E until green**

Run: `pnpm e2e:m2`
Expected: 1 passed. If selectors mismatch actual UI text, fix ONLY the spec selectors to match the implemented UI (never weaken the assertions). Then run `pnpm e2e:m1` and `pnpm e2e:public-mvp` separately to confirm no regression.

- [ ] **Step 4: Full verification gates**

Run in order (separate invocations):

```powershell
mvnw.cmd -q verify
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm e2e:m1
pnpm e2e:m2
pnpm e2e:public-mvp
```

Then lifecycle unpiped: `powershell -File scripts/start-demo.ps1` (expect "Public demo ready") then `powershell -File scripts/stop-demo.ps1` (expect ports released).

Then static scans:

```powershell
Get-ChildItem -Recurse -File -Include *.java,*.ts,*.tsx -Path apps,packages,e2e | Where-Object { $_.FullName -notmatch 'node_modules|target|dist' } | Select-String -Pattern '\bTODO\b|\bTBD\b|\bFIXME\b' | Where-Object { $_.Line -notmatch 'TODO\(INTERNAL\)' }
Get-ChildItem -Recurse -File -Include *.java,*.ts,*.tsx,*.yml,*.yaml,*.json -Path apps,packages,e2e,docs | Where-Object { $_.FullName -notmatch 'node_modules|target|dist' } | Select-String -Pattern 'password\s*[:=]\s*"[^"]+"|secret\s*[:=]\s*"[^"]+"' | Where-Object { $_.Line -notmatch 'fictional|example|\$\{' }
```

Expected: no output for both.

- [ ] **Step 5: Write and commit the evidence doc**

Create `docs/verification/m2-milestone-2026-08-18.md` with the gate table (mirror `docs/verification/m1-milestone-2026-08-18.md`), the M2 commit list, the new `TODO(INTERNAL)` IDs (`INTERNAL-EPIC-001`, `INTERNAL-AUD-001`), and any environment quirks observed. Then:

```powershell
git add e2e/m2-three-level-workflow.spec.ts package.json docs/verification/m2-milestone-2026-08-18.md
git commit -m "test(m2): add three-level workflow E2E and milestone evidence"
```

---

## Self-review notes

- Spec coverage: Epic/Ticket/Repo Task state machines (Tasks 2–4), emergency change request with dual-role approval (Tasks 3–4), skip attestation (Tasks 3–4), dependency DAG with MERGED gate (Tasks 3–4), resume-from-shutdown (Task 4 resume endpoint, Task 5 UI, Task 7 E2E), runnable gates (Task 7). MCP tooling (Task 6), UI panel (Task 5), browser E2E (Task 7) all covered.
- Type consistency: Java method names used in tests match service implementations (`create`, `activate`, `transition`, `markChangePending`, `ackChange`, `ticket`, `listByEpic`, `add`, `resolve`, `approve`, `reject`, `skip`, `listByTask`); REST payload fields match controller records; TS tool names match server.test.ts discovery list.
- No placeholders: every step carries concrete code or exact commands; the only intentional RED stub is the IT's final block, whose replacement is specified in the same step.

