# M2 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M2 — Three-level workflow (Epic → Ticket → Repo Task) with emergency change requests, stage skip attestation, a dependency merge gate, and resume-from-shutdown context

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (unit + IT, exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (vscode-extension 9, contracts 23, workflow-mcp 11, ui 8, web-ui 5) |
| Node builds | `pnpm build` | PASS (all workspaces incl. vscode-extension) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| M2 browser E2E | `pnpm e2e:m2` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` | PASS — "Public demo ready", health UP, web 200 |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` | PASS — ports 8080/4173 released |
| TODO/TBD scan | workspace scan excluding `TODO(INTERNAL)` | NONE |
| Credential scan | workspace scan | NONE |

## Commits in this milestone

- `cc3732c` feat(m2): add three-level workflow contract types
- `72d4cfc` feat(m2): add three-level workflow domain records and repositories
- `d0787cc` feat(m2): add three-level workflow services with change, skip, and dependency rules
- `9512df9` fix(m2): serialize aggregate mutations and cover remaining skip states
- `6f5cf5b` feat(m2): add epic REST controller, wiring, and integration tests
- `28c8c5e` fix(m2): scope conflict mapping to a domain exception and cover skip and auth paths
- `74a543c` feat(m2): add epic workflow panel to the Web demo
- `c03003c` fix(m2): separate merge gate from errors and add panel guidance
- `5b74073` feat(m2): add epic and skip tools to the local workflow MCP
- `436ebdb` fix(m2): align epic id schema and cover resume tool

## Environment quirks observed (not code defects)

1. The M1 quirks still apply: do NOT pipe `start-demo.ps1`/`stop-demo.ps1` through any consumer (the spawned service processes keep the pipe open and the wrapper hangs), and do NOT run two Playwright suites back-to-back inside a single shell invocation (the first runner can crash and orphan the auto-started webServer processes). All suites and lifecycle steps in this gate were run as separate invocations and released ports cleanly.
2. pnpm v10 prints `[WARN] The "pnpm" field in package.json is no longer read ... pnpm.overrides` on every `pnpm` invocation. This is a pre-existing deprecation notice only; the install/build/test commands all succeed.
3. Playwright webServer processes print `NO_COLOR`/`FORCE_COLOR` Node warnings at startup; cosmetic only.
4. Committing on Windows emits `LF will be replaced by CRLF` line-ending warnings; expected with the repository's autocrlf setting.

## Internal handoff status

- New internal configuration points are registered in `docs/handoff/INTERNAL_TODO.md`: `INTERNAL-EPIC-001` (Jira sync for Epic creation and Ticket status changes, in `api/EpicController.java`) and `INTERNAL-AUD-001` (MongoDB persistence for the M2 domain aggregates and audit events, in `config/FakeRuntimeConfiguration.java` and `config/MongoRuntimeConfiguration.java`).
- All M2 behavior is verified against the `fake` profile with fictitious data. Jira sync and Mongo persistence remain internal-agent work behind the registered markers.
