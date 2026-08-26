# M3 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M3 — Enterprise adapters: Jira projection outbox (draft → human-confirmed publish → retry with `JIRA_ARTIFACT_SYNC_PENDING`/`FAILED`), Jenkins CI status flow into the Ticket View, and Splunk structured audit emission with allowlist redaction

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (105 tests — 85 unit + 20 IT, 0 failures, exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (vscode-extension 13, contracts 30, workflow-mcp 11, ui 8, web-ui 5) |
| Node builds | `pnpm build` | PASS (all workspaces incl. vscode-extension) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| M2 browser E2E | `pnpm e2e:m2` | PASS (1 spec) |
| M3 browser E2E | `pnpm e2e:m3` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` | PASS — "Public demo ready" |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` | PASS — ports 8080/4173 released |
| TODO/TBD scan | apps,packages,e2e excluding `TODO(INTERNAL)` | NONE |
| Credential scan | apps,packages,e2e,docs,central | NONE |

## Commits in this milestone

- `71fc9d1` docs: add M3 enterprise adapters implementation plan
- `e6a85e5` feat(m3): add the Jira projection outbox with retry and max attempts
- `0f592d9` feat(m3): add Jira projection and Jenkins CI endpoints with wiring
- `a36098e` fix(m3): scope the publish endpoint to a single projection and drop the dead version body
- `7dc7b56` feat(m3): emit allowlisted Splunk audit events for projections and CI
- `314654c` fix(m3): record the CI state in Splunk audits and pin fail-open behavior
- `693c65a` feat(m3): add the Jira projection and Jenkins CI panel

## Environment quirks observed (not code defects)

1. The M1/M2 quirks still apply: do NOT pipe `start-demo.ps1`/`stop-demo.ps1` through any consumer (the spawned service processes keep the pipe open and the wrapper hangs), and do NOT run two Playwright suites back-to-back inside a single shell invocation (the first runner can crash and orphan the auto-started webServer processes). All suites and lifecycle steps in this gate were run as separate invocations and released ports cleanly.
2. `.\mvnw.cmd -q verify` runs in Maven quiet mode, so the `BUILD SUCCESS` banner is suppressed; the exit code (0) and the surefire/failsafe reports (85 unit + 20 IT, 0 failures/errors/skipped) are the pass indicators.
3. During `verify`, `JiraProjectionIT.recordsJenkinsCiAndAdvancesTheTicket` logs a WARN `Splunk audit publish failed; event dropped` because the deterministic fake transport has no scenario scripted for the `ci_status` event. This is the intended fail-open path (commit `314654c`), not a defect; the test still passes.
4. pnpm v10 prints `[WARN] The "pnpm" field in package.json is no longer read ... pnpm.overrides` on every `pnpm` invocation. This is a pre-existing deprecation notice only; the install/build/test commands all succeed.
5. Playwright webServer processes print `NO_COLOR`/`FORCE_COLOR` Node warnings at startup; cosmetic only.
6. Committing on Windows emits `LF will be replaced by CRLF` line-ending warnings; expected with the repository's autocrlf setting.

## Internal handoff status

- New internal configuration points are registered in `docs/handoff/INTERNAL_TODO.md`: `INTERNAL-JIRA-001` (route the Jira projection outbox to the real Jira comment API, in `api/EpicController.java` and `config/*RuntimeConfiguration.java`), `INTERNAL-CI-001` (route CI status to the real Jenkins adapter, in `api/EpicController.java` and `config/*RuntimeConfiguration.java`), and `INTERNAL-SPLUNK-001` (point the Splunk audit publisher at the real HEC endpoint, in `config/*RuntimeConfiguration.java`).
- All M3 behavior is verified against the `fake` profile with fictitious data. Jira comment publish, Jenkins CI, and Splunk HEC remain internal-agent work behind the registered markers.
