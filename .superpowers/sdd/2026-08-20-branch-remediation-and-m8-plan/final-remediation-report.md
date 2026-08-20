# Final remediation wave report

Date: 2026-08-21

Implementation commit: `3d00f8e399858495dd307d494cb4059bf1fb7b59`

Review range addressed: `f2f2871..1f996bd`

The controller-owned edit to
`docs/superpowers/plans/2026-08-20-branch-remediation-and-m8-plan.md` was
deliberately excluded from the implementation commit. No push was performed.

## Outcome by final-review finding

1. Hook runtime: `hookCommand` now uses ordinary Node directly and adds
   `--ms-enable-electron-run-as-node` when `process.execPath` is a VS Code /
   Electron executable. A Code.exe-shaped regression covers the extension-host
   case.
2. Transactional customization activation: all three global customization
   location settings, the global hook setting, active-location Memento,
   installer-managed-hook Memento, and installed-bundle Memento are snapshotted
   before activation. Any failed write compensates every participant. A newly
   published destination is removed when activation fails. The regression
   injects failure at each of the seven write boundaries and asserts both VS
   Code configuration and global state are restored.
3. Refresh identity gate: `TaskPoller` no longer accepts or evaluates a
   demo-identity predicate. Initial, periodic, and focus refreshes execute even
   when `sdlc.demoActorId` is empty; HTTP 401/offline responses become visible
   tree/readiness states.
4. Freshness and last-known data: freshness is evaluated from observation time
   when a view renders, so LIVE naturally becomes DELAYED and STALE. Remote and
   local view providers retain last-known rows after refresh failure and prepend
   an explicit warning containing the current freshness.
5. Repository-scoped idempotency: new task keys contain ticket, repository,
   revision, and task type. The requirement-analysis legacy key is consulted
   only when its persisted task has the same repository scope. A two-repository
   same-ref regression proves separation.
6. Jira projection: the server summary contains only server-controlled ticket
   ID, artifact type, and approval state. No artifact-authored section title or
   body is read. Credential-bearing and ordinary title regressions prove they
   cannot appear.
7. M7 evidence: aggregate transitions now follow completed work rather than
   being replayed at the end. Persisted `PR_REVIEW` and `MANUAL_E2E` tasks are
   executed. The driver queries Epic resume, Ticket, Repo Task, task list, task
   audit, and Epic audit state and rejects incomplete evidence. Browser coverage
   displays and asserts ACTIVE Epic, E2E_VERIFIED Ticket, MERGED Repo Task,
   completed true stage types, and persisted audit evidence.
8. Windows temporal lineage: process identity comparison uses a 100-ms start
   tolerance, and descendant discovery rejects a child that predates its parent
   identity. The lineage helper regression models an old child under a reused
   parent PID; the existing process-tree, UTC identity, PID-reuse, quiescence,
   and discovery-failure cases remain covered.
9. Internal TODO registry: validation now enforces object shape, exact fields,
   strict ID syntax, non-empty string types, normalized source-root paths,
   unique marker paths, malformed marker detection, JSON/Markdown/source
   parity, and one explicit `INTERNAL-XXX` exception in the completion-report
   template only.
10. Delivery inventory: the public manifest now records the actual 23 Workflow
    MCP tools, 13 central Agents, 33 Skills, 19 Instructions, and eight VSIX
    views, and states why those bounded outputs remain PARTIAL against the
    approved seven-repository/internal target.

## RED evidence

- `pnpm --filter sdlc-workbench test -- taskPoller.test.ts viewState.test.ts viewE2e.test.ts bundleInstaller.test.ts`
  failed 5 regressions before implementation: Electron runtime flag,
  transactional cleanup, empty-actor refresh, elapsed freshness, and ungated
  polling.
- `.\mvnw.cmd -q -pl apps/workflow-service test '-Dtest=WorkflowApiIT,JiraProjectionIT'`
  failed 3 regressions before implementation: cross-repository legacy-key
  collision and two artifact-title projection leaks.
- `node --test scripts/tests/internalTodoRegistry.test.mjs` failed malformed
  marker and strict-field cases before implementation. A later strict-schema
  mutation also failed the unknown-field/duplicate-path regression as expected.
- Controlled M7 mutation (`PR_REVIEW` changed to a non-persisted type) made
  `pnpm --filter @sdlc/web-ui test -- fictionalSdlcDriver.test.ts` fail with
  `persisted stage types do not match requested stages`.
- Controlled lineage mutation (temporal predicate forced true) made
  `scripts/tests/stop-demo.test.ps1` fail with `Temporal lineage accepted a
  child that predates the parent identity`.

Both controlled mutations were restored before the GREEN and full verification
runs.

## GREEN and affected public verification

| Command | Fresh result |
|---|---|
| `.\mvnw.cmd -q verify` | PASS; 119 tests in 40 Surefire reports, 0 failures/errors/skips |
| `pnpm test` | PASS; 116 tests (Contracts 34, Workflow MCP 12, VSIX 56, UI 8, Web 6) |
| `pnpm build` | PASS; all five runnable workspaces built |
| `pnpm e2e:m7` | PASS; Playwright 1/1 with persisted lifecycle evidence |
| `node --test scripts/tests/internalTodoRegistry.test.mjs` | PASS; 11/11 |
| `pnpm verify:internal-todos` | PASS; 10 IDs and 19 canonical marker paths |
| `powershell -File scripts/tests/stop-demo.test.ps1` | PASS; temporal lineage, PID reuse, tree, exit, and discovery failure |
| `pnpm --filter sdlc-workbench package` | PASS; typecheck, build, 6-file 17.65-KB VSIX |
| `scripts/tests/build-bundle.test.ps1` | PASS |
| `scripts/tests/bundle-lifecycle.test.ps1` | PASS |
| `git diff --check` | PASS; only repository-configured LF/CRLF conversion notices |

Post-mutation focused rerun: Web M7 1/1, internal registry 11/11,
stop-demo lifecycle PASS, and affected VSIX suites 40/40.

## Changed files

- VSIX runtime/activation/freshness:
  `apps/vscode-extension/src/customization/bundleInstaller.ts`,
  `src/extension.ts`, `src/polling/taskPoller.ts`, `src/views/{types,viewState,
  customizationProvider,epicProvider,identityPodProvider,mcpCenterProvider,
  myWorkProvider,scrumMasterProvider,ticketProvider}.ts`, with matching
  `bundleInstaller`, `taskPoller`, `viewE2e`, `viewState`, and `views` tests.
- Workflow security/idempotency:
  `WorkflowTaskController.java`, `WorkflowTaskService.java`,
  `JiraSummaryFactory.java`, `WorkflowApiIT.java`, and `JiraProjectionIT.java`.
- Truthful M7: `apps/web-ui/src/fictionalSdlcDriver.ts`, its unit test, and
  `e2e/m7-end-to-end.spec.ts`.
- Windows lifecycle: `scripts/process-lineage.psm1`, `scripts/stop-demo.ps1`,
  and `scripts/tests/stop-demo.test.ps1`.
- Handoff validator/inventory: `scripts/lib/internalTodoRegistry.mjs`, its test,
  `docs/handoff/{INTERNAL_TODO.md,internal-todo-registry.json,
  PUBLIC_DELIVERY_MANIFEST.md}`.

## Caveats and retained boundaries

- All reasoning still occurs only in interactive local VS Code GitHub Copilot.
  No Docker, cloud Agent, server-side model client, or real company endpoint was
  added.
- Public verification uses the fake runtime. Company Mongo persistence, SSO,
  Jira/GHES/Jenkins/Splunk connectivity, policy, and release signing remain
  internal work.
- VS Code exposes no atomic transaction spanning configuration and Memento.
  Activation therefore uses explicit snapshots and compensation. If the host
  itself refuses compensation writes, the original activation error is still
  raised and the newly created bundle directory is removed; company-host chaos
  testing remains advisable.
- The Electron command regression verifies construction for a Code.exe-shaped
  runtime; execution against the company's exact VS Code build remains an
  internal compatibility check.
- M7 evidence is genuinely persisted/query-derived inside the fictional
  in-memory public service. It is not evidence of company-system connectivity
  or restart durability.
- The approved seven-repository split and `INTERNAL-AUD-001` Mongo persistence
  remain intentionally pending and are not reported complete.
