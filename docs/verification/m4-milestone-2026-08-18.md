# M4 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M4 — Journey/repo onboarding: journey freshness engine (`LIVE`/`DELAYED`/`STALE`/`OFFLINE`), freshness-badged HTML Journey report, fictitious Account Opening sample data, and a browser E2E that observes a repository live, marks another stale, and refreshes the report.

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (112 tests — 91 unit + 21 IT, 0 failures, exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (vscode-extension 13, contracts 30, workflow-mcp 11, ui 8, web-ui 5) |
| Node builds | `pnpm build` | PASS (all workspaces incl. vscode-extension) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| M2 browser E2E | `pnpm e2e:m2` | PASS (1 spec) |
| M3 browser E2E | `pnpm e2e:m3` | PASS (1 spec) |
| M4 browser E2E | `pnpm e2e:m4` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` | PASS — "Public demo ready" |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` | PASS — ports 8080/4173 released |
| TODO/TBD scan | apps,packages,e2e excluding `TODO(INTERNAL)` | NONE |
| Credential scan | apps,packages,e2e,docs,central | NONE |

## Commits in this milestone

- `a5428f0` docs(m3): correct the INTERNAL-SPLUNK-001 marker location
- `f5d71d0` docs: add M4 journey onboarding implementation plan
- `cd403f0` feat(m4): add the journey freshness engine
- `647fcad` test(m4): pin staleMarked priority over delayed freshness
- `7214cbc` feat(m4): expose journey freshness and badge the HTML report
- `e029dca` feat(m4): load the fictitious Account Opening journey and show freshness badges

## Environment quirks observed (not code defects)

1. The M1/M2/M3 quirks still apply: do NOT pipe `start-demo.ps1`/`stop-demo.ps1` through any consumer (the spawned service processes keep the pipe open and the wrapper hangs), and do NOT run two Playwright suites back-to-back inside a single shell invocation (the first runner can crash and orphan the auto-started webServer processes). All suites and lifecycle steps in this gate were run as separate invocations and released ports cleanly.
2. `.\mvnw.cmd -q verify` runs in Maven quiet mode, so the `BUILD SUCCESS` banner is suppressed; the exit code (0) and the surefire/failsafe reports (91 unit + 21 IT, 0 failures/errors/skipped) are the pass indicators.
3. During `verify`, `JiraProjectionIT.recordsJenkinsCiAndAdvancesTheTicket` logs a WARN `Splunk audit publish failed; event dropped` because the deterministic fake transport has no scenario scripted for the `ci_status` event. This is the intended fail-open path (commit `314654c`), not a defect; the test still passes.
4. The M4 report iframe is sandboxed (`sandbox="" srcDoc={...}`); a sandboxed `<iframe srcDoc>` exposes empty element text to Playwright, so `toContainText("Evidence status: CONTRACT_PASS")` cannot pass. The E2E asserts the rendered report via `toHaveAttribute("srcdoc", expect.stringContaining("Evidence status: CONTRACT_PASS"))` instead.
5. pnpm v10 prints `[WARN] The "pnpm" field in package.json is no longer read ... pnpm.overrides` on every `pnpm` invocation. This is a pre-existing deprecation notice only; the install/build/test commands all succeed.
6. Playwright webServer processes print `NO_COLOR`/`FORCE_COLOR` Node warnings at startup; cosmetic only.
7. Committing on Windows emits `LF will be replaced by CRLF` line-ending warnings; expected with the repository's autocrlf setting.

## Internal handoff status

- New internal configuration point registered in `docs/handoff/INTERNAL_TODO.md`: `INTERNAL-JOURNEY-001` (feed real repository observation events (merge hooks) into the freshness engine and persist observations in MongoDB, in `api/JourneyController.java` and `config/*RuntimeConfiguration.java`).
- All M4 behavior is verified against the `fake` profile with fictitious data. Repository observation events and MongoDB persistence remain internal-agent work behind the registered marker.
