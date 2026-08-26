# Branch Remediation and M8 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the reviewed M1–M7 safety and workflow defects, then complete M8's internal replacement registry, CI check, and handoff evidence without pushing unreviewed commits.

**Architecture:** Keep the approved run-first fake profile intact. Fix the public behavioral contract at its source: server-side Jira summary derivation, real Repo Task and stage lifecycles, safe bundle copying, live VSIX selection, then machine-readable internal replacement tracking. Mongo persistence remains explicitly deferred to `INTERNAL-AUD-001` and cannot be reported as complete.

**Tech Stack:** Java 17/Spring Boot, TypeScript/VS Code extension, React/Vite/Playwright, Node 20 standard library, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-18-run-first-public-milestones-design.md`; `docs/superpowers/specs/2026-08-16-seven-repository-platform-design.md`; `docs/reviews/2026-08-20-deepseek-continuation-audit.md`.

## Global Constraints

- Only interactive local VS Code GitHub Copilot Chat may use AI reasoning.
- No Docker, local MongoDB, S3, GridFS, cloud Agent, server-side Agent, real endpoint, credential or company data.
- Add failing tests before every behavioral fix.
- Do not push until the complete unpushed branch has passed a final review.

## Task 1: Safe server-generated Jira projection

**Files:**

- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/JiraProjectionService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/JiraSummaryFactory.java`
- Modify: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JiraProjectionIT.java`

**Interfaces:**

```java
public record JiraProjectionRequest(String ticketId, String milestoneId, String artifactId, int artifactVersion) {}
public String JiraSummaryFactory.create(TicketWorkflow ticket, ArtifactMetadata artifact)
```

- [ ] Write integration tests that reject an unknown ticket, reject an unknown artifact, reject an artifact not created for that ticket's task, and prove caller text cannot appear in the projection.
- [ ] Run the focused integration test and confirm the old free-text API cannot satisfy the new contract.
- [ ] Replace `summary` request input with `artifactId` and `artifactVersion`; load ticket and approved artifact in the controller; derive a bounded plain-text summary from artifact type, ticket ID, approved state, and title-only/whitelisted summary section metadata.
- [ ] Cap the generated summary at 500 characters; redact control characters, URLs, and secret-like key/value fields; never include an artifact body, code, payload, email, or display name.
- [ ] Run `mvnw.cmd -q -pl apps/workflow-service test -Dtest=JiraProjectionIT` and commit the focused change.

## Task 2: Bundle symlink boundary and Windows-safe hook shim

**Files:**

- Modify: `apps/vscode-extension/src/customization/bundleInstaller.ts`
- Modify: `apps/vscode-extension/src/customization/bundleManifest.ts`
- Create: `central/hooks/run-hook.mjs`
- Modify: `apps/vscode-extension/test/bundleInstaller.test.ts`
- Modify: `apps/vscode-extension/test/bundleManifest.test.ts`

**Interfaces:**

```ts
export async function rejectBundleSymlinks(root: string): Promise<void>
export function hookCommand(root: string, action: string): string
```

- [ ] Add tests that construct a selected bundle containing a symlink in a skill directory and assert installation rejects before any copy; add a test that the hook command calls Node plus the installed `run-hook.mjs` path and contains no shell redirection.
- [ ] Run the extension tests and confirm the symlink and Windows command tests fail under current behavior.
- [ ] Recursively inspect every copied manifest/shipped source with `lstat`; reject a symbolic link rather than dereferencing it. Validate this before creating destination content.
- [ ] Replace the POSIX echo command with a quote-safe Node invocation of the installed no-op hook script. The hook script validates its one allowlisted action argument and exits zero without printing sensitive data.
- [ ] Run focused extension tests, TypeScript build, and VSIX packaging; commit the focused change.

## Task 3: Reachable Repo Task lifecycle and honest M7 execution

**Files:**

- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/EpicController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/repotask/RepoTaskService.java` only if transition policy needs correction
- Modify: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/EpicWorkflowIT.java`
- Modify: `apps/workflow-mcp/src/tools/workflowTools.ts`
- Modify: `apps/workflow-mcp/test/epicTools.test.ts`
- Modify: `apps/web-ui/src/fictionalSdlcDriver.ts`
- Modify: `apps/web-ui/src/fictionalSdlcDriver.test.ts`
- Modify: `e2e/m7-end-to-end.spec.ts`

**Interfaces:**

```java
POST /api/v1/repo-tasks/{repoTaskId}/advance { expectedVersion, target }
```

```ts
advance_repo_task({ repoTaskId, expectedVersion, target })
```

- [ ] Add a failing API test that creates a Repo Task and advances it through the existing transition policy to a terminal merged/released state; reject stale versions and invalid transitions.
- [ ] Add a failing MCP test for the matching tool request/response.
- [ ] Expose the controller endpoint and Local MCP tool with strict status enum and expected-version validation; do not add a second state machine.
- [ ] Change M7 to create one Epic, one Ticket, and one Repo Task, then create stage-specific workflow tasks with genuine task type input or a new server endpoint that accepts the allowed stage type. Do not relabel a requirement-analysis task as another stage.
- [ ] Advance the Repo Task through PR, review/CI, merge and release evidence; make Playwright assert the Repo Task ID, each true stage type, CI, manual E2E, and final status.
- [ ] Run Java, MCP, Web unit tests and `pnpm e2e:m7`; commit the focused change.

## Task 4: Live Epic selection for Ticket and Scrum Master views

**Files:**

- Modify: `apps/vscode-extension/src/views/types.ts`
- Create: `apps/vscode-extension/src/views/epicSelection.ts`
- Modify: `apps/vscode-extension/src/views/epicProvider.ts`
- Modify: `apps/vscode-extension/src/views/ticketProvider.ts`
- Modify: `apps/vscode-extension/src/views/scrumMasterProvider.ts`
- Modify: `apps/vscode-extension/src/extension.ts`
- Modify: `apps/vscode-extension/test/views.test.ts`
- Modify: `apps/vscode-extension/test/viewE2e.test.ts`

**Interfaces:**

```ts
export interface EpicSelection { selectedEpicId(): string | undefined; select(epicId: string): void; onDidChange: vscode.Event<void>; }
```

- [ ] Add failing tests for no selected Epic (empty state), automatic first live Epic selection, and switching selection so Ticket/Scrum queries use the selected ID rather than `EPIC-M2-1`.
- [ ] Implement the selection service in extension scope. Epic Provider selects an Epic through a command or tree action; Ticket and Scrum providers subscribe and refresh.
- [ ] Keep fictional fixture values only in tests/demo. Remove `FIRST_EPIC_ID` from production view lookup.
- [ ] Run extension unit tests, view E2E, build, and package; commit the focused change.

## Task 5: M8 deterministic internal TODO registry and CI

**Files:**

- Create: `docs/handoff/internal-todo-registry.json`
- Modify: `docs/handoff/INTERNAL_TODO.md`
- Create: `scripts/lib/internalTodoRegistry.mjs`
- Create: `scripts/validate-internal-todos.mjs`
- Create: `scripts/tests/internalTodoRegistry.test.mjs`
- Create: `.github/workflows/verify-internal-todos.yml`
- Modify: `package.json`
- Modify: `docs/handoff/internal-agent-completion-report-template.md`

**Interfaces:**

```js
validateRegistry({ rootDirectory })
// returns { markerCount, registryCount, errors }
```

- [ ] Write red tests for the checked-in registry, an unregistered marker, a JSON entry without marker, a marker-path mismatch, and Markdown/JSON ID mismatch.
- [ ] JSON registry entries contain `id`, `component`, `markerPaths`, `action`, `evidence`, and `rollback`; include every existing `INTERNAL-…` ID.
- [ ] Scan only `apps`, `packages`, and `central`, skipping generated/dependency directories. Require the exact marker expression `TODO(INTERNAL): INTERNAL-…`.
- [ ] Add `pnpm verify:internal-todos` and a Node 20 GitHub Actions workflow that runs it on relevant pull-request/push changes.
- [ ] Add the required per-ID status/evidence/deviation/rollback table to the internal completion report template.
- [ ] Run the focused registry tests and CLI; commit the focused change.

## Task 6: M8 evidence and final branch verification

**Files:**

- Create: `docs/verification/m8-milestone-2026-08-20.md`
- Modify: `docs/handoff/PUBLIC_DELIVERY_MANIFEST.md`

- [ ] Run `mvnw.cmd -q verify`, frozen pnpm install, `pnpm test`, `pnpm build`, every public M1/M2/M3/M4/M7/browser regression, bundle scripts, lifecycle scripts, `pnpm verify:internal-todos`, and static secret/unregistered-TODO scans.
- [ ] Record exact command outcomes/counts, registry counts, known Mongo limitation (`INTERNAL-AUD-001`), and that seven-repository split is still pending.
- [ ] Update manifest statuses only with evidence; do not call the target platform complete.
- [ ] Request a final code review over `origin/agent/mvp-vertical-slice..HEAD`; fix Critical/Important findings or document technically justified deferrals.
- [ ] Commit verification docs only after fresh evidence exists.

## Plan Self-Review

- Critical and Important review findings R-01/R-03/R-04/R-05/R-06 are concrete tasks with tests.
- R-02 and R-08 are explicit sequencing limitations, represented by internal IDs and final evidence rather than misreported completion.
- R-07 is included in Task 2 because Windows is a public-run target.
- No task adds a model client, Docker dependency, secret, or real company integration.

## Execution Approach

The user selected Inline execution previously and asked to continue. Execute Tasks 1–6 sequentially; request review after each substantive task and do not push until Task 6 has fresh full-branch evidence.

## Task 6A: Windows demo-process shutdown confirmation

**Files:**

- Modify: `scripts/stop-demo.ps1`
- Modify: `scripts/tests/stop-demo.test.ps1` only to make the existing UTC/PID regression assert the shutdown-confirmation contract explicitly

**Root cause and scope:** The existing test consistently fails because `Stop-Process` returns before Windows has removed the process from the process table. `stop-demo.ps1` currently removes its state file immediately after issuing the stop request, while the test immediately observes the PID. This task must not change application services, alter the PID-reuse identity rule, or add a fixed arbitrary sleep.

- [ ] Add or strengthen a failing lifecycle regression that records a UTC-identified `powershell Start-Sleep` process and asserts the stop script returns only after that PID cannot be retrieved.
- [ ] Run the test and record the expected pre-fix failure caused by an immediately observable PID.
- [ ] Add a bounded, condition-based process-exit wait to `stop-demo.ps1` after it has stopped the allowlisted process tree and before it removes `.demo/processes.json`. If a process remains at timeout, throw and retain the state file for diagnosis.
- [ ] Re-run the lifecycle regression, then run an unpiped start-demo/stop-demo smoke check with port-release verification; commit the focused correction.

## Task 6B: M3 Jira-projection browser precondition

**Files:**

- Modify: `e2e/m3-enterprise-adapters.spec.ts`

**Root cause and scope:** Jira draft projection is intentionally derived from a persisted, approved artifact. The current M3 browser scenario still expects the removed free-text `DEMO-123` draft button, so it does not establish its approved-artifact precondition. The UI's disabled button and server-side projection contract are correct and must not be weakened.

- [ ] Run the current M3 scenario and record its expected failure on the obsolete `Draft Jira comment for DEMO-123` selector.
- [ ] Change the browser scenario to run the fictional M7 SDLC flow, wait for its completed audit evidence, then invoke `Draft Jira comment from approved M7 artifact`.
- [ ] Assert the resulting draft/published Jira status remains derived from `REQ-APPROVED`, and retain the existing M2 Repo Task/Jenkins CI assertions.
- [ ] Run the M3 scenario in a clean server lifecycle, then commit the focused test-contract repair.

## Final remediation wave: whole-branch review findings

**Scope:** Correct all Critical/Important findings from final review `f2f2871..1f996bd` in one coherent fix wave, with tests before behavioral changes. Do not push. Do not loosen the public constraints.

- [ ] Resolve a real Node runtime for installed VSIX hooks (Electron/extension-host compatible), and prove it with an extension-host-style executable test.
- [ ] Make bundle activation transactional across global location settings, installer state, and hook settings; compensate partial updates and test every write boundary.
- [ ] Make VSIX initial, periodic, and focus refresh independent of the demo-only actor setting; present authentication/offline results rather than silently disabling refresh.
- [ ] Make freshness reflect elapsed time after data observation and preserve last known data across failed refreshes, with an observable delayed/stale refresh path.
- [ ] Include repository identity in new workflow-task idempotency keys, retain compatible legacy matching only for the same repository scope, and test two repositories sharing a ref.
- [ ] Derive Jira summaries only from fixed server-controlled metadata; never publish artifact-authored title/body text. Add credential-bearing title regression coverage.
- [ ] Make M7 lifecycle order truthful: transition Ticket/Repo Task with appropriate stage work, add real PR-review/manual-E2E stage work when those types exist, and return/assert persisted service audit/state evidence instead of client labels.
- [ ] Make Windows descendant identification temporal as well as PID-parent based so an old child of a reused parent PID is never terminated; test the regression.
- [ ] Enforce strict internal-TODO registry field types, ID syntax, marker path types, and malformed-marker detection while excluding only explicit template placeholders.
- [ ] Regenerate the public delivery manifest's actual MCP tool, agent, and VSIX-view inventory and explain any remaining partial status accurately.
- [ ] Run focused regression suites, then all affected public verification suites; commit the remediation wave for one scoped final re-review.
