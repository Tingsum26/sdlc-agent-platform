# M6 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M6 — VSIX 8 independent views: My Work (landing), Scrum Master, Epic, Ticket Detail (nested Repo Task), Identity/Pod Configuration, Customization Center, MCP Center, Diagnostics — each with its own view model, distinct data loading, freshness badges (LIVE/DELAYED/STALE/OFFLINE), offline/error states, and accessibility, verified by per-view model tests and an extension-level E2E.

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (vscode-extension 37, contracts 34, workflow-mcp 11, ui 8, web-ui 5) |
| Node builds | `pnpm build` | PASS (all workspaces) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| M2 browser E2E | `pnpm e2e:m2` | PASS (1 spec) |
| M3 browser E2E | `pnpm e2e:m3` | PASS (1 spec) |
| M4 browser E2E | `pnpm e2e:m4` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Bundle build test | `powershell -File scripts/tests/build-bundle.test.ps1` | PASS |
| Bundle lifecycle E2E | `powershell -File scripts/tests/bundle-lifecycle.test.ps1` | PASS |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` (unpiped) | PASS — "Public demo ready" |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` (unpiped) | PASS — ports 8080/4173 released |
| TODO/TBD scan | apps,packages,e2e excluding `TODO(INTERNAL)` | NONE |
| Credential scan | apps,packages,e2e,docs,central | NONE |

## Commits in this milestone

- `7792d82` docs: add M6 VSIX views implementation plan
- `2c4b2a7` docs(m6): record the viewState test determinism deviation
- `d179cdd` feat(m6): add view-state freshness and offline/error semantics
- `3f65986` feat(m6): extend the workflow client for epic, ticket, pod, and freshness data
- `eb041e6` fix(m6): add the GET epics endpoint and tighten the client contract
- `5d34ad2` feat(m6): add one distinct view provider per workbench view
- `27e9230` feat(m6): wire eight independent views with isolated refresh
- `720389a` test(m6): add view-level E2E, accessibility, and catalog parity assertions

## Environment quirks observed (not code defects)

1. The M1–M5 quirks still apply: run Playwright suites as separate invocations; NEVER pipe `start-demo.ps1`/`stop-demo.ps1` through any consumer (the wrapper hangs — reproduced again this milestone; unpiped runs are clean).
2. The packaged VSIX ships only `dist/` per `package.json` `files`, so the MCP Center uses a static catalog mirror of `central/mcp/catalog.json`; a parity test guards server ids + required flags against the central catalog.
3. `vi.useFakeTimers` in the view E2E fakes only the poller timers; `Date.now()` stays real and no freshness value is asserted in the E2E, so no boundary flake.
4. Real VS Code rendering of the 8 views remains internal verification (`TODO(INTERNAL)` boundary); the public side pins provider logic, nesting, isolation, and accessibility through the mocked-vscode E2E.

## Internal handoff status

- No new `TODO(INTERNAL)` markers were needed for M6 (no new internal-network configuration points; the extension talks only to the local Workflow Service).
- `INTERNAL-HOOKS-001` (M5) remains the only new handoff item pending company policy confirmation.
