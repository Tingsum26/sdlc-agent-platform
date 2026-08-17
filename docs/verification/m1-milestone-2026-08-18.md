# M1 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M1 — Identity and Pod routing (run-first public environment)

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (unit + IT, exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (contracts, workflow-mcp 8, ui 8, web-ui 5) |
| Node builds | `pnpm build` | PASS (all workspaces incl. vscode-extension) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` | PASS — "Public demo ready", health UP, web 200 |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` | PASS — ports 8080/4173 released |
| TODO/TBD scan | workspace scan excluding `TODO(INTERNAL)` | NONE (all raw hits were the `toDomain` method-name substring) |
| Credential scan | workspace scan | NONE |

## Commits in this milestone

- `92a86f9` feat(m1): add one-time enrollment codes for non-GitHub identities
- `ea67abd` fix(m1): make enrollment code consumption atomic
- `a47ffba` feat(m1): add directory persons with onboarding status
- `548abf5` feat(m1): expose enrollment, binding, and roster member endpoints
- `a81329f` fix(m1): return roster role with onboarding status and assert bind flip end to end
- `0eff4af` feat(m1): add Pod roster CSV import and assignment UI
- `d673318` fix(m1): surface assignment failures instead of unhandled rejection
- `6ce7c6d` test(m1): add identity and Pod routing browser E2E

## Environment quirks observed (not code defects)

1. Do NOT pipe `start-demo.ps1`/`stop-demo.ps1` through `Select-Object` or any other consumer in an automated shell: the spawned service processes can keep the captured pipe open and the wrapper hangs even though the stack is healthy. Run the scripts unpiped.
2. Running two Playwright suites back-to-back inside a single shell invocation can crash the first Node runner (exit `0xC0000409`) and orphan the auto-started webServer processes, which then block ports 8080/4173. Run suites as separate invocations; if ports stay occupied, clean up with `stop-demo.ps1` and any orphan java/node listeners.

## Internal handoff status

- New internal configuration points are registered in `docs/handoff/INTERNAL_TODO.md`: `INTERNAL-IDN-001`, `INTERNAL-IDN-002`, `INTERNAL-POD-001`.
- All M1 behavior is verified against the `fake` profile with fictitious data (`EMP-100`, `example.invalid`). Real SSO/Teambook/roster provisioning remains internal-agent work.
