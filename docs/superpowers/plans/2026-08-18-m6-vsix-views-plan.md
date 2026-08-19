# M6 VSIX 8 Independent Views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make milestone M6 runnable: the VSIX workbench exposes exactly the 8 approved views — My Work (landing), Scrum Master, Epic, Ticket Detail (with nested Repo Task children), Identity/Pod Configuration, Customization Center, MCP Center, Diagnostics — each backed by its own view model with distinct data loading, freshness badges (LIVE/DELAYED/STALE/OFFLINE), offline/error states, and accessibility, verified by per-view model tests and an extension-level E2E.

**Architecture:** Replace the shared `TaskTreeProvider` (which currently backs 8 of the 9 registered view ids) with one provider class per view. A shared `ViewState` helper carries `data/lastUpdated/freshness/error/offline` semantics; `WorkflowClient` gains the endpoints the new views need (epics, tickets, repo tasks, pod members, journey freshness). `extension.ts` wires each provider with isolated per-view refresh (one view's failure never breaks another). `package.json` contributes exactly the 8 views (Developer View removed; Repo Task becomes a nested level inside Ticket View).

**Tech Stack:** TypeScript VSIX extension (vitest), mocked `vscode`.

**Working directory:** `D:\codex\sdlc-agent-platform\.worktrees\agent-mvp-vertical-slice`

**Existing seed (read first):** `apps/vscode-extension/src/extension.ts` (viewIds of 9, all task views share `TaskTreeProvider`), `views/taskTreeProvider.ts`, `views/readinessTreeProvider.ts`, `views/readinessModel.ts`, `api/workflowClient.ts` (listTasks/getTask/approve/getIdentity/getIntegrationDiagnostics/getNextInternalValidation/renderJourneyReport), `test/{extension,workflowClient,readinessModel,taskPoller}.test.ts`.

---

### Task 1: View-state foundation (freshness + offline/error)

**Files:**
- Create: `apps/vscode-extension/src/views/viewState.ts`
- Test: `apps/vscode-extension/test/viewState.test.ts`

- [ ] **Step 1: Write the failing test**

`apps/vscode-extension/test/viewState.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { computeFreshness, toViewState, type ViewState } from "../src/views/viewState.js";

describe("view state", () => {
  it("computes freshness from staleness age", () => {
    const now = Date.parse("2026-08-18T12:00:00Z");
    expect(computeFreshness(now - 60_000, now)).toBe("LIVE");
    expect(computeFreshness(now - 6 * 60_000, now)).toBe("DELAYED");
    expect(computeFreshness(now - 16 * 60_000, now)).toBe("STALE");
    expect(computeFreshness(undefined, now)).toBe("OFFLINE");
  });

  it("builds a loading, data, and error state", () => {
    const loading = toViewState<{ n: number }>({ kind: "loading" });
    expect(loading).toMatchObject({ kind: "loading", freshness: "OFFLINE" });
    const data = toViewState<{ n: number }>({ kind: "data", data: { n: 1 }, at: Date.parse("2026-08-18T12:00:00Z") });
    expect(data).toMatchObject({ kind: "data" });
    expect(data.freshness).toBe("LIVE");
    const error = toViewState<{ n: number }>({ kind: "error", message: "boom" });
    expect(error).toMatchObject({ kind: "error", freshness: "OFFLINE" });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter sdlc-workbench test`
Expected: FAIL — `viewState` module missing.

- [ ] **Step 3: Implement `views/viewState.ts`**

> Implementation note (2026-08-18): Task 1's test 2 used a pinned date `Date.parse("2026-08-18T12:00:00Z")` against `toViewState`'s default `now = Date.now()`, which is date-brittle (LIVE only within 5 minutes of that instant). The committed test uses `at: Date.now() - 60_000` instead; the module itself is unchanged.

```ts
export type Freshness = "LIVE" | "DELAYED" | "STALE" | "OFFLINE";
export type ViewState<T> =
  | { kind: "loading" }
  | { kind: "data"; data: T; at: number }
  | { kind: "error"; message: string };

export const LIVE_WINDOW_MS = 5 * 60_000;
export const DELAYED_WINDOW_MS = 15 * 60_000;

export function computeFreshness(at: number | undefined, now = Date.now()): Freshness {
  if (at === undefined) return "OFFLINE";
  const age = now - at;
  if (age <= LIVE_WINDOW_MS) return "LIVE";
  if (age <= DELAYED_WINDOW_MS) return "DELAYED";
  return "STALE";
}

export function toViewState<T>(state: ViewState<T>, now = Date.now()): ViewState<T> & { freshness: Freshness } {
  if (state.kind === "data") return { ...state, freshness: computeFreshness(state.at, now) };
  return { ...state, freshness: "OFFLINE" };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter sdlc-workbench test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```powershell
git add apps/vscode-extension/src/views/viewState.ts apps/vscode-extension/test/viewState.test.ts
git commit -m "feat(m6): add view-state freshness and offline/error semantics"
```

---

### Task 2: WorkflowClient endpoints for the new views

**Files:**
- Modify: `apps/vscode-extension/src/api/workflowClient.ts`
- Test: `apps/vscode-extension/test/workflowClient.test.ts`

- [ ] **Step 1: Write the failing tests (append to `workflowClient.test.ts`)**

```ts
describe("M6 workflow client", () => {
  it("lists epics", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response("[]", { status: 200, headers: { "content-type": "application/json" } }));
    const client = new WorkflowClient("http://127.0.0.1:8080", fetcher);
    await client.listEpics();
    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/epics", expect.objectContaining({ method: "GET" }));
  });

  it("loads an epic resume with tickets and repo tasks", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (url: string | URL | Request) => {
      const path = String(url);
      if (path.endsWith("/resume")) return new Response(JSON.stringify({ epic: {}, tickets: [], auditTrail: [] }), { status: 200, headers: { "content-type": "application/json" } });
      if (path.includes("/repo-tasks")) return new Response("[]", { status: 200, headers: { "content-type": "application/json" } });
      return new Response("[]", { status: 200, headers: { "content-type": "application/json" } });
    });
    const client = new WorkflowClient("http://127.0.0.1:8080", fetcher);
    const resume = await client.getEpicResume("EPIC-M2-1");
    expect(resume.tickets).toEqual([]);
  });

  it("loads pod members", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response("[]", { status: 200, headers: { "content-type": "application/json" } }));
    const client = new WorkflowClient("http://127.0.0.1:8080", fetcher);
    await client.getPodMembers("ACCOUNT_OPENING");
    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/internal-readiness/pods/ACCOUNT_OPENING/members", expect.anything());
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter sdlc-workbench test`
Expected: FAIL — methods missing.

- [ ] **Step 3: Implement the client additions**

Add to `api/workflowClient.ts` (follow the existing `request` helper pattern; add types):

```ts
export interface EpicSummary { epicId: string; title: string; journeyId: string; status: string; version: number }
export interface TicketSummary { ticketId: string; epicId: string; channel: string; status: string; pendingChangeConfirmation: boolean; version: number }
export interface RepoTaskSummary { repoTaskId: string; ticketId: string; repositoryAlias: string; status: string; version: number }
export interface EpicResume { epic: EpicSummary; tickets: Array<{ ticket: TicketSummary; openTasks: unknown[]; nextAction: string }>; auditTrail: Array<{ action: string; actorId: string; occurredAt: string }> }
export interface PodMember { principalId: string; employeeId: string; displayLabel: string; role: string; onboardingStatus: string }
export interface JourneyFreshnessMap { [alias: string]: string }

  async listEpics(signal?: AbortSignal): Promise<EpicSummary[]> {
    const result = await this.request("/api/v1/epics", { method: "GET" }, signal);
    return result as EpicSummary[];
  }

  async getEpicResume(epicId: string, signal?: AbortSignal): Promise<EpicResume> {
    return (await this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/resume`, { method: "GET" }, signal)) as EpicResume;
  }

  async listTickets(epicId: string, signal?: AbortSignal): Promise<TicketSummary[]> {
    return (await this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/tickets`, { method: "GET" }, signal)) as TicketSummary[];
  }

  async listRepoTasks(ticketId: string, signal?: AbortSignal): Promise<RepoTaskSummary[]> {
    return (await this.request(`/api/v1/tickets/${encodeURIComponent(ticketId)}/repo-tasks`, { method: "GET" }, signal)) as RepoTaskSummary[];
  }

  async getPodMembers(journeyId: string, signal?: AbortSignal): Promise<PodMember[]> {
    return (await this.request(`/api/v1/internal-readiness/pods/${encodeURIComponent(journeyId)}/members`, { method: "GET" }, signal)) as PodMember[];
  }

  async getJourneyFreshness(manifest: unknown, signal?: AbortSignal): Promise<JourneyFreshnessMap> {
    return (await this.request("/api/v1/journeys/freshness", { method: "POST", body: JSON.stringify(manifest) }, signal)) as JourneyFreshnessMap;
  }
```

(Adapt the `request` helper signature if it differs — read the file first. If the helper takes `(path, init, signal)` with no correlation id, keep that shape; otherwise thread what exists.)

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter sdlc-workbench test`
Expected: PASS (existing + 3 new).

- [ ] **Step 5: Commit**

```powershell
git add apps/vscode-extension/src/api/workflowClient.ts apps/vscode-extension/test/workflowClient.test.ts
git commit -m "feat(m6): extend the workflow client for epic, ticket, pod, and freshness data"
```

---

### Task 3: Distinct view providers (8 views)

**Files:**
- Create: `apps/vscode-extension/src/views/myWorkProvider.ts`
- Create: `apps/vscode-extension/src/views/scrumMasterProvider.ts`
- Create: `apps/vscode-extension/src/views/epicProvider.ts`
- Create: `apps/vscode-extension/src/views/ticketProvider.ts` (ticket items with nested RepoTask children)
- Create: `apps/vscode-extension/src/views/identityPodProvider.ts`
- Create: `apps/vscode-extension/src/views/customizationProvider.ts`
- Create: `apps/vscode-extension/src/views/mcpCenterProvider.ts`
- Modify: `apps/vscode-extension/src/views/taskTreeProvider.ts` (delete — superseded; or repurpose as MyWork's tree if simpler; the plan prefers dedicated providers, so delete it and update imports in Task 4)
- Tests: one per provider in `apps/vscode-extension/test/` (e.g. `views.test.ts` covering all providers with a mocked client + vscode)

- [ ] **Step 1: Write the failing tests**

Create `apps/vscode-extension/test/views.test.ts` (mock `vscode` with `EventEmitter`, `TreeItem`, `TreeItemCollapsibleState`, `ThemeIcon`; construct each provider with a fake client and assert distinct content):

```ts
import { afterEach, describe, expect, it, vi } from "vitest";

const eventEmitter = () => ({ event: vi.fn(), fire: vi.fn() });
vi.mock("vscode", () => ({
  EventEmitter: vi.fn(() => eventEmitter()),
  TreeItem: class { constructor(public label: string, public collapsibleState?: number) {} },
  TreeItemCollapsibleState: { None: 0, Collapsed: 1, Expanded: 2 },
  ThemeIcon: class { constructor(public id: string) {} },
}));

import * as vscode from "vscode";
import { MyWorkProvider } from "../src/views/myWorkProvider.js";
import { ScrumMasterProvider } from "../src/views/scrumMasterProvider.js";
import { EpicProvider } from "../src/views/epicProvider.js";
import { TicketProvider } from "../src/views/ticketProvider.js";
import { IdentityPodProvider } from "../src/views/identityPodProvider.js";
import { CustomizationProvider } from "../src/views/customizationProvider.js";
import { McpCenterProvider } from "../src/views/mcpCenterProvider.js";

const task = { taskId: "TASK-1", type: "REQUIREMENT_ANALYSIS", status: "WAITING_FOR_LOCAL_COPILOT", scope: { ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567" }, version: 0, updatedAt: "2026-08-18T00:00:00Z" };

describe("view providers", () => {
  afterEach(() => vi.clearAllMocks());

  it("my work shows only actionable tasks with freshness", () => {
    const client = { listTasks: vi.fn().mockResolvedValue([task]) };
    const provider = new MyWorkProvider(client as never);
    void provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.length).toBe(1);
    expect(items[0]!.label).toContain("DEMO-123");
  });

  it("ticket view nests repo tasks under tickets", async () => {
    const client = {
      listTickets: vi.fn().mockResolvedValue([{ ticketId: "M2-API-1", epicId: "EPIC-M2-1", channel: "API", status: "PR_OPEN", pendingChangeConfirmation: false, version: 5 }]),
      listRepoTasks: vi.fn().mockResolvedValue([{ repoTaskId: "REPO-TASK-1", ticketId: "M2-API-1", repositoryAlias: "REPO_A", status: "MERGED", version: 2 }]),
    };
    const provider = new TicketProvider(client as never);
    await provider.refresh();
    const ticketItem = provider.getChildren() as vscode.TreeItem[];
    expect(ticketItem.length).toBe(1);
    const children = await provider.getChildren(ticketItem[0]);
    expect((children as vscode.TreeItem[])[0]!.label).toContain("REPO-TASK-1");
  });

  it("identity view lists pod members", async () => {
    const client = {
      getIdentity: vi.fn().mockResolvedValue({ employeeId: "EMP-100", displayLabel: "Fictional Scrum Master", source: "ADMIN_BINDING" }),
      getPodMembers: vi.fn().mockResolvedValue([{ principalId: "PRINCIPAL-EMP-201", employeeId: "EMP-201", displayLabel: "Fictional Developer", role: "DEVELOPER", onboardingStatus: "NOT_ONBOARDED" }]),
    };
    const provider = new IdentityPodProvider(client as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.some((item) => String(item.label).includes("EMP-201"))).toBe(true);
  });
});
```

Note: the fake client types are minimal (`as never`) — each provider must accept an interface-typed client; the tests cast. Add equivalent tests for EpicProvider (resume status), ScrumMasterProvider (next actions), CustomizationProvider (installed versions from a globalState-like store), McpCenterProvider (catalog counts) — keep them small.

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter sdlc-workbench test`
Expected: FAIL — provider modules missing.

- [ ] **Step 3: Implement the providers**

Each provider class implements `vscode.TreeDataProvider<vscode.TreeItem>` with:
- `readonly onDidChangeTreeData = this.changed.event` (EventEmitter pattern from `taskTreeProvider.ts`)
- a `refresh()` that loads its data through the client, stores a `ViewState<T>` (from Task 1), fires `changed`, and swallows errors into the `error` state (never throws)
- `getChildren(element?)`: root-level items from the current state; when the view has a nested level (Ticket → RepoTask), `getChildren(item)` returns the repo tasks for that ticket
- each root item carries: label, description (secondary info), tooltip (includes freshness/updated time), `accessibilityInformation` (`label` combining label+description+status so screen readers get the state), and an icon only where it adds signal (pass/error/info ThemeIcon like the readiness provider)
- an explicit `No data` / `Error: <message>` root item when the state is empty/error/offline

Provider specifics:
- `MyWorkProvider(client)`: root = actionable tasks (`!["COMPLETED","CANCELLED"].includes(status)`); description = repositoryAlias; tooltip includes freshness of the poll.
- `ScrumMasterProvider(client)`: root = next-action hints for the first epic's tickets from `getEpicResume(EPIC-M2-1)` (label `ticketId · status`, description `nextAction`).
- `EpicProvider(client)`: root = `listEpics()` items (label `epicId · title`, description `status`).
- `TicketProvider(client)`: root = `listTickets("EPIC-M2-1")`; children via `listRepoTasks(ticketId)` (label `repoTaskId · status`, description `repositoryAlias`).
- `IdentityPodProvider(client)`: root = identity line + `getPodMembers("ACCOUNT_OPENING")` rows (label `employeeId · displayLabel`, description `role · onboardingStatus`).
- `CustomizationProvider(store)`: root = installed bundle versions from a store-shaped `{ get(key, fallback) }` (label `version`, description `installedAt`) + the three command items previously in `taskTreeProvider` for this view.
- `McpCenterProvider(catalog)`: root = catalog servers + skill counts (label `server id`, description `required/optional`) + the "Open MCP onboarding" command.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter sdlc-workbench test`
Expected: PASS (existing + the views tests).

- [ ] **Step 5: Commit**

```powershell
git add apps/vscode-extension/src/views apps/vscode-extension/test/views.test.ts
git commit -m "feat(m6): add one distinct view provider per workbench view"
```

---

### Task 4: Extension wiring — 8 views with isolated per-view refresh

**Files:**
- Modify: `apps/vscode-extension/src/extension.ts`
- Modify: `apps/vscode-extension/package.json` (views contribution: remove `sdlc.developer` and top-level `sdlc.repoTask`; keep the 8 view ids)
- Modify: `apps/vscode-extension/test/extension.test.ts`

- [ ] **Step 1: Write the failing tests (extend `extension.test.ts`)**

Read `test/extension.test.ts` first; add assertions that:
- the view list registered is exactly the 8 ids: `["sdlc.myWork", "sdlc.scrumMaster", "sdlc.epic", "sdlc.ticket", "sdlc.identityPod", "sdlc.customization", "sdlc.mcpCenter", "sdlc.diagnostics"]` (rename `sdlc.repoTask`→ nested; new `sdlc.identityPod` id),
- `sdlc.developer` and a top-level `sdlc.repoTask` are NOT registered,
- a per-view refresh failure does not prevent other views from refreshing (a client whose `getPodMembers` throws still lets `listTasks` succeed).

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter sdlc-workbench test`
Expected: FAIL — extension still registers 9 ids.

- [ ] **Step 3: Update `package.json` views contribution**

Replace the `sdlcWorkbench` view list with exactly:

```json
[
  { "id": "sdlc.myWork", "name": "My Work" },
  { "id": "sdlc.scrumMaster", "name": "Scrum Master View" },
  { "id": "sdlc.epic", "name": "Epic View" },
  { "id": "sdlc.ticket", "name": "Ticket View" },
  { "id": "sdlc.identityPod", "name": "Identity / Pod Configuration" },
  { "id": "sdlc.customization", "name": "Customization Center" },
  { "id": "sdlc.mcpCenter", "name": "MCP Center" },
  { "id": "sdlc.diagnostics", "name": "Diagnostics" }
]
```

(Remove `sdlc.developer` and `sdlc.repoTask`; add `sdlc.identityPod`.)

- [ ] **Step 4: Update `extension.ts`**

- Replace the `viewIds` array with the 8 ids above.
- Construct one provider per id: `MyWorkProvider`, `ScrumMasterProvider`, `EpicProvider`, `TicketProvider`, `IdentityPodProvider`, `CustomizationProvider`, `McpCenterProvider`, `ReadinessTreeProvider` (Diagnostics; keep as-is, align naming if desired).
- Replace the single `refresh()` that pushed the same tasks into 8 providers with a per-provider refresh fan-out: each provider's `refresh()` runs independently; wrap each in try/catch so one failure never breaks the others; the status bar + logger stay as the aggregate summary.
- Keep `sdlc.openTask`, `sdlc.openReport`, `sdlc.copyCopilotCommand`, `sdlc.installCustomizationBundle`, `sdlc.rollbackCustomizationBundle`, `sdlc.checkMcpHealth`, `sdlc.openMcpCenter` commands; the CustomizationProvider and McpCenterProvider render their command items, so remove the old special-casing inside `taskTreeProvider.ts` and delete that file.
- Keep the diagnostics view command registration via the readiness provider.

- [ ] **Step 5: Run tests**

Run: `pnpm --filter sdlc-workbench test` — all green. Then `pnpm --filter sdlc-workbench build` and `pnpm --filter sdlc-workbench exec tsc --noEmit` — green.

- [ ] **Step 6: Commit**

```powershell
git add apps/vscode-extension/src/extension.ts apps/vscode-extension/package.json apps/vscode-extension/test/extension.test.ts
git commit -m "feat(m6): wire eight independent views with isolated refresh"
```

---

### Task 5: Accessibility pass and extension E2E

**Files:**
- Modify: provider files from Task 3 (accessibility polish: consistent `accessibilityInformation`, non-color status text, focus-friendly labels)
- Modify: `apps/vscode-extension/test/views.test.ts` (add a11y assertions)
- Create: `apps/vscode-extension/test/viewE2e.test.ts` (extension-level E2E: activate with a mocked client, assert each of the 8 registered tree providers returns its own distinct content and that Ticket nests Repo Task; Diagnostics shows the health rows)

- [ ] **Step 1: Write the failing E2E test**

`apps/vscode-extension/test/viewE2e.test.ts` (follow the mock style from Task 3; import `activate` from `../src/extension.js` with a mocked `vscode` that provides `window.createOutputChannel`, `window.createWebviewPanel`, `window.state`, `workspace.getConfiguration`, `commands.registerCommand`, `EventEmitter`, `TreeItem`, etc. — read `test/extension.test.ts` first and reuse its mock harness):

```ts
it("activates eight distinct views and tickets nest repo tasks", async () => {
  const { activate } = await import("../src/extension.js");
  const context = { subscriptions: [], globalState: { get: vi.fn(() => []), update: vi.fn() }, globalStorageUri: { fsPath: tmpdir() } } as unknown as vscode.ExtensionContext;
  activate(context);
  // assert 8 registerTreeDataProvider calls with distinct provider instances and no sdlc.developer/sdlc.repoTask ids
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter sdlc-workbench test`
Expected: FAIL or the test is not yet meaningful — iterate until it asserts the 8-distinct-providers property.

- [ ] **Step 3: Accessibility polish**

- Every root/child item sets `accessibilityInformation = { label: "<label>. <description>. <status/freshness>" }` (mirror `readinessTreeProvider.ts:25`).
- Status is conveyed as text (e.g. description suffix `· LIVE` / `· OFFLINE`), never only via icon/color.
- Empty and error states are explicit `TreeItem`s with clear labels ("No actionable tasks", "Error: <message> · retry with SDLC: Refresh Tasks").

- [ ] **Step 4: Run tests**

Run: `pnpm --filter sdlc-workbench test` — all green; `build` + `typecheck` green.

- [ ] **Step 5: Commit**

```powershell
git add apps/vscode-extension/src/views apps/vscode-extension/test
git commit -m "test(m6): add view-level E2E and accessibility assertions"
```

---

### Task 6: Full gates and evidence

**Files:**
- Create: `docs/verification/m6-milestone-2026-08-18.md`

- [ ] **Step 1: Full gates (separate invocations)**

```powershell
.\mvnw.cmd -q verify
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm e2e:m1
pnpm e2e:m2
pnpm e2e:m3
pnpm e2e:m4
pnpm e2e:public-mvp
powershell -File scripts/tests/build-bundle.test.ps1
powershell -File scripts/tests/bundle-lifecycle.test.ps1
powershell -File scripts/start-demo.ps1
powershell -File scripts/stop-demo.ps1
```

Expected: all green; lifecycle ports released. Then the two static scans — expect no output.

- [ ] **Step 2: Evidence doc**

Create `docs/verification/m6-milestone-2026-08-18.md` mirroring the M5 doc: gate table, the M6 commit list (`git log --oneline f2f2871..HEAD` minus the evidence commit), new `TODO(INTERNAL)` IDs if any (e.g. none expected; note that real VS Code view rendering remains internal verification), quirks.

- [ ] **Step 3: Commit**

```powershell
git add docs/verification/m6-milestone-2026-08-18.md
git commit -m "test(m6): record milestone verification evidence"
```

---

## Self-review notes

- Spec coverage: 8 views (Tasks 1–4), distinct view models with freshness/offline/error (Tasks 1–3), Ticket nests Repo Task (Task 3), Diagnostics health (existing readiness provider retained), per-view tests + extension E2E (Tasks 3–5), gates + evidence (Task 6). Developer View removed and Repo Task nested per the v2 8-view model.
- Type consistency: `ViewState`/`Freshness` from Task 1 are used by every provider; the client method names from Task 2 match the provider calls and the extension wiring in Task 4; the 8 view ids in package.json match `extension.ts` and the extension test.
- No placeholders: every step has concrete code or commands; provider specifics are enumerated per view.

