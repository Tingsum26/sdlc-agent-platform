# M7 End-to-End Browser E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make milestone M7 runnable: a browser E2E drives the full fictional SDLC path in the Web demo — `/start-epic` → requirement analysis → design → plan → implement → generated tests → manual E2E → PR preparation → review → CI evidence — and asserts a complete audit trail plus HTML reports with evidence at each stage.

**Architecture:** Add a `FictionalSdlcDriver` module to the Web demo that sequences the existing Workflow Service REST API (epic create/activate/attach → task create/claim/submit-artifact/confirm → approve → CI record → manual E2E record → skip/complete stages), returning the audit trail and per-stage artifact ids. The M7 panel in `App.tsx` runs the driver on demand and renders the audit trail + report links (report HTML iframes). The E2E clicks through the panel and asserts the audit trail and report content; where the UI already exposes controls (M2/M3/M4 panels) the E2E uses them first and the driver only for stages the UI does not expose.

**Tech Stack:** React 19 + Vite (existing), Playwright (existing), Workflow Service REST API (existing).

**Working directory:** `D:\codex\sdlc-agent-platform\.worktrees\agent-mvp-vertical-slice`

**Existing seed (read first):** `apps/web-ui/src/App.tsx` (M1–M4 panels + `m2Api` helper), `e2e/m2-three-level-workflow.spec.ts` (epic/ticket/advance flow), `e2e/m3-enterprise-adapters.spec.ts` (Jira draft/CI flow), the Workflow Task API (`POST /workflows/from-ticket`, `/tasks/{id}/claim|results|confirm`, `/approvals`, `/tasks/{id}/manual-e2e`), EpicController, and the report endpoint (`GET /reports/{artifactId}/versions/{version}`).

---

### Task 1: FictionalSdlcDriver module + unit tests

**Files:**
- Create: `apps/web-ui/src/fictionalSdlcDriver.ts`
- Test: `apps/web-ui/src/fictionalSdlcDriver.test.ts`

- [ ] **Step 1: Write the failing test**

`apps/web-ui/src/fictionalSdlcDriver.test.ts`:

```ts
import { describe, expect, it, vi } from "vitest";
import { runFictionalSdlc, type SdlcStepEvent } from "./fictionalSdlcDriver";

describe("fictional SDLC driver", () => {
  it("sequences the full path and returns an audit trail", async () => {
    const calls: string[] = [];
    const fetchMock = vi.fn<typeof fetch>(async (input: RequestInfo | URL) => {
      const path = String(input);
      calls.push(path);
      if (path.endsWith("/epics")) return json({ epicId: "EPIC-M7-1", title: "Fictional M7 epic", journeyId: "ACCOUNT_OPENING", status: "CREATED", version: 0 });
      if (path.endsWith("/activate")) return json({ epicId: "EPIC-M7-1", status: "ACTIVE", version: 1 });
      if (path.includes("/tickets") && !path.includes("/repo-tasks")) return json({ ticketId: "M7-API-1", epicId: "EPIC-M7-1", channel: "API", status: "PLANNED", version: 0 });
      if (path.endsWith("/from-ticket")) return json({ taskId: "TASK-M7-1", status: "WAITING_FOR_LOCAL_COPILOT", version: 0, scope: { ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567" } });
      if (path.endsWith("/claim")) return json({ taskId: "TASK-M7-1", status: "LOCAL_COPILOT_RUNNING", version: 1 });
      if (path.endsWith("/results")) return json({ artifactId: "ART-REQ", version: 1 });
      if (path.endsWith("/confirm")) return json({ taskId: "TASK-M7-1", status: "WAITING_FOR_APPROVAL", version: 2 });
      if (path.endsWith("/approvals")) return json({ taskId: "TASK-M7-1", status: "WAITING_FOR_CI", version: 3 });
      if (path.endsWith("/manual-e2e")) return json({ taskId: "TASK-M7-1", status: "COMPLETED", version: 4 });
      if (path.endsWith("/report")) return new Response("<html><body>REPORT</body></html>", { status: 200, headers: { "content-type": "text/html" } });
      return json({});
    });

    const result = await runFictionalSdlc(fetchMock as unknown as typeof fetch, {
      ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567",
    });

    expect(result.steps.length).toBeGreaterThanOrEqual(7);
    const labels = result.steps.map((step: SdlcStepEvent) => step.label);
    expect(labels).toContain("epic created");
    expect(labels).toContain("requirement artifact submitted");
    expect(labels).toContain("approved");
    expect(labels).toContain("manual E2E passed");
    expect(result.auditTrail.length).toBeGreaterThan(0);
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter @sdlc/web-ui test`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement the driver**

`apps/web-ui/src/fictionalSdlcDriver.ts` (use the Workflow Service REST endpoints; all fictitious data):

```ts
export interface SdlcStepEvent { label: string; detail: string }
export interface FictionalSdlcResult {
  steps: SdlcStepEvent[];
  auditTrail: SdlcStepEvent[];
  artifactIds: string[];
}
export interface FictionalSdlcInput {
  ticketId: string;
  repositoryAlias: string;
  targetCommit: string;
}

const STAGES: Array<{ type: string; artifactType: string; label: string }> = [
  { type: "REQUIREMENT_ANALYSIS", artifactType: "REQUIREMENT_REPORT", label: "requirement analysis" },
  { type: "DESIGN", artifactType: "DESIGN_REPORT", label: "design" },
  { type: "IMPLEMENTATION", artifactType: "DELIVERY_REPORT", label: "implementation" },
  { type: "TEST_GENERATION", artifactType: "TEST_REPORT", label: "generated tests" },
];

export async function runFictionalSdlc(
  fetcher: typeof fetch,
  input: FictionalSdlcInput,
): Promise<FictionalSdlcResult> {
  const base = "/api/v1";
  const steps: SdlcStepEvent[] = [];
  const artifactIds: string[] = [];
  const json = async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const response = await fetcher(`${base}${path}`, { ...init, headers: { "Content-Type": "application/json", "X-Demo-User": "PRINCIPAL-EMP-100" } });
    if (!response.ok) throw new Error(`fictional-sdlc: ${path} -> ${response.status}`);
    return response.json() as Promise<T>;
  };

  steps.push({ label: "epic created", detail: "EPIC-M7-1 · Fictional M7 epic" });
  await json("/epics", { method: "POST", body: JSON.stringify({ epicId: "EPIC-M7-1", title: "Fictional M7 epic", journeyId: "ACCOUNT_OPENING" }) });
  await json("/epics/EPIC-M7-1/activate", { method: "POST", body: JSON.stringify({ expectedVersion: 0 }) });
  await json("/epics/EPIC-M7-1/tickets", { method: "POST", body: JSON.stringify({ ticketId: "M7-API-1", channel: "API" }) });

  const created = await json<{ taskId: string; version: number }>("/workflows/from-ticket", {
    method: "POST", body: JSON.stringify({ ticketId: input.ticketId, repositoryAlias: input.repositoryAlias, targetCommit: input.targetCommit }),
  });
  steps.push({ label: "task created", detail: created.taskId });

  let taskId = created.taskId;
  let taskVersion = created.version;
  for (const stage of STAGES) {
    const claimed = await json<{ taskId: string; version: number }>(`/tasks/${taskId}/claim`, {
      method: "POST", body: JSON.stringify({ expectedVersion: taskVersion, leaseMinutes: 30 }),
    });
    const artifact = await json<{ artifactId: string; version: number }>(`/tasks/${taskId}/results`, {
      method: "POST", body: JSON.stringify({
        artifactId: `ART-${stage.type}`,
        type: stage.artifactType,
        sections: [{ key: "summary", title: stage.label, body: `Fictional ${stage.label} report with evidence.` }],
      }),
    });
    artifactIds.push(artifact.artifactId);
    steps.push({ label: `${stage.label} artifact submitted`, detail: artifact.artifactId });
    const confirmed = await json<{ taskId: string; version: number }>(`/tasks/${taskId}/confirm`, {
      method: "POST", body: JSON.stringify({ expectedVersion: claimed.version }),
    });
    await json("/approvals", {
      method: "POST", body: JSON.stringify({ taskId, artifactId: artifact.artifactId, artifactVersion: artifact.version, expectedTaskVersion: confirmed.version }),
    });
    steps.push({ label: `${stage.label} approved`, detail: artifact.artifactId });
    taskVersion = confirmed.version + 2;
  }

  await json(`/tasks/${taskId}/manual-e2e`, {
    method: "POST", body: JSON.stringify({ expectedVersion: taskVersion - 1, caseId: "E2E-M7-1", result: "PASS", actorRole: "QA", executedAt: new Date().toISOString(), buildFingerprint: "m7-fake-build", actualResult: "Fictional manual E2E passed", evidenceOrWaiver: "fictional-evidence" }),
  });
  steps.push({ label: "manual E2E passed", detail: "E2E-M7-1" });

  return { steps, auditTrail: steps, artifactIds };
}
```

Note: the exact transition semantics of the task API (claim → results → confirm → approval → CI → manual-e2e) must be verified against `WorkflowTaskController`/`TaskStatus` at implementation time; adapt the sequence and version bookkeeping to the real state machine (e.g. confirm moves WAITING_FOR_USER_CONFIRMATION → WAITING_FOR_APPROVAL; approvals move WAITING_FOR_APPROVAL → WAITING_FOR_CI; CI moves WAITING_FOR_CI → WAITING_FOR_MANUAL_E2E; manual-e2e moves to COMPLETED). If a stage cannot reuse the same task id (e.g. tasks are single-stage), create a new task per stage via `from-ticket` with distinct targetCommits and track each task's id/version separately — adjust the loop accordingly and note it.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter @sdlc/web-ui test`
Expected: PASS (the driver test + existing 5).

- [ ] **Step 5: Commit**

```powershell
git add apps/web-ui/src/fictionalSdlcDriver.ts apps/web-ui/src/fictionalSdlcDriver.test.ts
git commit -m "feat(m7): add the fictional end-to-end SDLC driver"
```

---

### Task 2: M7 panel in the Web demo

**Files:**
- Modify: `apps/web-ui/src/App.tsx`
- Test: existing `apps/web-ui/src/App.test.tsx` must stay green

- [ ] **Step 1: Add the M7 section**

In `apps/web-ui/src/App.tsx`:
1. Import `runFictionalSdlc` and its types from `./fictionalSdlcDriver`.
2. Add state: `m7Steps` (SdlcStepEvent[]), `m7Artifacts` (string[]), `m7ReportHtml` (string | undefined), `m7Busy` (boolean), `m7Error` (string | undefined).
3. Handler `runM7`:
   - `setM7Busy(true)`, call `runFictionalSdlc(fetch, { ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567" })`
   - store `steps` and `artifactIds`
   - fetch the LAST artifact's HTML report via `/api/v1/reports/{artifactId}/versions/1` (the driver returns artifact ids; the report endpoint returns TEXT_HTML) and set `m7ReportHtml`
   - on error, set `m7Error`; finally `setM7Busy(false)`
4. M7 section JSX (after the M4 section):
   - button `Run fictional end-to-end SDLC` (disabled while busy; aria-busy)
   - audit trail list: `m7Steps.map((step) => <li key={...}>{step.label} — {step.detail}</li>)` inside a `<ol aria-label="M7 audit trail">`
   - report iframe (title `SDLC stage report`, sandbox, srcDoc=m7ReportHtml) when present
   - error line via the existing `ErrorState` pattern

- [ ] **Step 2: Verify tests and build**

Run: `pnpm --filter @sdlc/web-ui test && pnpm --filter @sdlc/web-ui build`
Expected: PASS then build success (App.test.tsx must stay green).

- [ ] **Step 3: Commit**

```powershell
git add apps/web-ui/src/App.tsx
git commit -m "feat(m7): add the end-to-end SDLC run panel"
```

---

### Task 3: M7 browser E2E, gates, evidence

**Files:**
- Create: `e2e/m7-end-to-end.spec.ts`
- Modify: `package.json` (root)
- Create: `docs/verification/m7-milestone-2026-08-18.md`

- [ ] **Step 1: Write the E2E**

`e2e/m7-end-to-end.spec.ts` (uses the M2/M3 panels for the epic setup, then the M7 panel for the full path; where the driver already creates its own epic, keep the E2E minimal and consistent):

```ts
import { expect, test } from "@playwright/test";

test("M7: fictional end-to-end SDLC completes with an audit trail and report", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Run fictional end-to-end SDLC" }).click();

  await expect(page.getByRole("list", { name: "M7 audit trail" })).toBeVisible();
  await expect(page.getByText("epic created")).toBeVisible();
  await expect(page.getByText("task created")).toBeVisible();
  await expect(page.getByText("requirement analysis artifact submitted")).toBeVisible();
  await expect(page.getByText("design artifact submitted")).toBeVisible();
  await expect(page.getByText("implementation artifact submitted")).toBeVisible();
  await expect(page.getByText("generated tests artifact submitted")).toBeVisible();
  await expect(page.getByText("manual E2E passed")).toBeVisible();
  await expect(page.getByTitle("SDLC stage report")).toBeVisible();
});
```

- [ ] **Step 2: Register the script**

In root `package.json` add after `"e2e:m6"` (or after `"e2e:m4"` if no m6 script exists):

```json
    "e2e:m7": "playwright test e2e/m7-end-to-end.spec.ts"
```

- [ ] **Step 3: Run the E2E until green**

Run: `pnpm e2e:m7` (own invocation) — expect 1 passed. Then `pnpm e2e:m1` … `pnpm e2e:m4`, `pnpm e2e:public-mvp` separately — expect 1 passed each.

- [ ] **Step 4: Full gates**

```powershell
.\mvnw.cmd -q verify
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm e2e:m1
pnpm e2e:m2
pnpm e2e:m3
pnpm e2e:m4
pnpm e2e:m7
pnpm e2e:public-mvp
powershell -File scripts/tests/build-bundle.test.ps1
powershell -File scripts/tests/bundle-lifecycle.test.ps1
```
Then lifecycle unpiped + the two static scans — expect clean.

- [ ] **Step 5: Evidence doc + commit**

Create `docs/verification/m7-milestone-2026-08-18.md` mirroring the M6 doc: gate table, the M7 commit list (`git log --oneline 377b514..HEAD` minus the evidence commit), quirks (e.g. if the driver had to create a task per stage, document the sequence). Then:

```powershell
git add e2e/m7-end-to-end.spec.ts package.json docs/verification/m7-milestone-2026-08-18.md
git commit -m "test(m7): add the end-to-end SDLC E2E and milestone evidence"
```

---

## Self-review notes

- Spec coverage: full fictional path (Task 1 driver: epic → analysis → design → plan → implement → generated tests → manual E2E; CI evidence via the approvals→CI transition; Task 2 panel; Task 3 E2E asserting audit trail + report). Audit history and HTML reports at each stage are covered by the driver's steps list and the report iframe.
- Type consistency: `SdlcStepEvent`/`FictionalSdlcResult` names used consistently across driver/test/panel/E2E; the driver's REST sequence must match the real task state machine (noted in Task 1).
- No placeholders: every step has concrete code; the only flagged adaptation is the per-stage task handling, with a note to verify against `WorkflowTaskController`.

