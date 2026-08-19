# M7 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M7 — a runnable fictional end-to-end SDLC in the Web demo: a `FictionalSdlcDriver` module sequences the existing Workflow Service REST API (epic create/activate/attach → per-stage task create/claim/submit-artifact/confirm/approve → CI record → manual E2E record) and returns the audit trail plus per-stage artifact ids; the M7 panel in `App.tsx` runs it on demand and renders the audit trail and the last stage's HTML report in a sandboxed iframe; a browser E2E clicks through the panel and asserts the complete audit trail and report evidence.

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (vscode-extension 37, contracts 34, workflow-mcp 11, ui 8, web-ui 6) |
| Node builds | `pnpm build` | PASS (all workspaces) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| M2 browser E2E | `pnpm e2e:m2` | PASS (1 spec) |
| M3 browser E2E | `pnpm e2e:m3` | PASS (1 spec) |
| M4 browser E2E | `pnpm e2e:m4` | PASS (1 spec) |
| M7 browser E2E | `pnpm e2e:m7` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Bundle build test | `powershell -File scripts/tests/build-bundle.test.ps1` | PASS |
| Bundle lifecycle E2E | `powershell -File scripts/tests/bundle-lifecycle.test.ps1` | PASS |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` (unpiped) | PASS — "Public demo ready" |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` (unpiped) | PASS — ports 8080/4173 released |
| TODO/TBD scan | apps,packages,e2e excluding `TODO(INTERNAL)` | NONE |
| Credential scan | apps,packages,e2e,docs,central | NONE (only redaction-test fixtures assert `password=[redacted]` behaviour on fictitious input) |

## Commits in this milestone

- `16278f3` docs: add M7 end-to-end implementation plan
- `85c1426` feat(m7): add the fictional end-to-end SDLC driver
- `edadcae` feat(m7): add the end-to-end SDLC run panel
- `2980ded` fix(m7): correct the fictional SDLC driver version bookkeeping

## Environment quirks observed (not code defects)

1. The M1–M6 quirks still apply: run Playwright suites as separate invocations; NEVER pipe `start-demo.ps1`/`stop-demo.ps1` through any consumer (unpiped runs are clean).
2. The Workflow Task API is single-stage: `POST /workflows/from-ticket` always creates a REQUIREMENT_ANALYSIS task keyed by `ticket:<ticketId>:<targetCommit>`. The M7 driver therefore creates a NEW task per SDLC stage (distinct `targetCommit` suffix `01`…`04`) and walks each task through the real state machine: from-ticket → claim → results → confirm → approvals → ci → manual-e2e.
3. The driver's first run against the real API failed on the first stage: `results` transitions the task internally (LOCAL_COPILOT_RUNNING → WAITING_FOR_USER_CONFIRMATION) and returns only the artifact, so the tracked version must be bumped after each `results` call or the following `confirm` throws `StaleTaskVersionException` (409). Fixed in `2980ded`; the driver's JSDoc version ladder was corrected to v0–v6.
4. Because a task is created per stage, the audit `<ol>` renders the `task created` and `manual E2E passed` labels once per stage (4 `<li>`s each). The M7 E2E asserts strict visibility for the per-stage-unique labels (`epic created`, the four `… artifact submitted` labels, the report iframe) and `toHaveCount(4)` for the duplicated labels, plus `srcdoc` content of the final `TEST_REPORT` iframe. (Review-mandated adjustment.)
5. The M7 run button is disabled once a run completes (`disabled={m7Busy || m7Steps.length > 0}`), mirroring the readiness section, so re-running cannot hit immutable-artifact failures (approved artifact versions cannot be replaced). (Review-mandated adjustment.)

## Internal handoff status

- No new `TODO(INTERNAL)` markers were needed for M7 (the driver uses only the existing public Workflow Service REST surface and fictitious data).
- `INTERNAL-HOOKS-001` (M5) remains the only pending handoff item awaiting company policy confirmation.
