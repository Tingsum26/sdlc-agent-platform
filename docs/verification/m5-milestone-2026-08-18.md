# M5 Milestone Verification (2026-08-18)

Branch: `agent/mvp-vertical-slice`
Milestone: M5 — Central bundle integration: hooks manifest + settings activation, MCP role profiles, no-Docker bundle build tool, evals mapping, and the build→install→rollback lifecycle.

## Gates

| Gate | Command | Result |
|---|---|---|
| Java full suite | `.\mvnw.cmd -q verify` | PASS (exit 0) |
| Frozen install | `pnpm install --frozen-lockfile` | PASS |
| Node tests | `pnpm test` | PASS (vscode-extension 18, contracts 34, workflow-mcp 11, ui 8, web-ui 5) |
| Node builds | `pnpm build` | PASS (all workspaces) |
| M1 browser E2E | `pnpm e2e:m1` | PASS (1 spec) |
| M2 browser E2E | `pnpm e2e:m2` | PASS (1 spec) |
| M3 browser E2E | `pnpm e2e:m3` | PASS (1 spec) |
| M4 browser E2E | `pnpm e2e:m4` | PASS (1 spec) |
| Regression E2E | `pnpm e2e:public-mvp` | PASS (1 spec) |
| Bundle build test | `powershell -File scripts/tests/build-bundle.test.ps1` | PASS |
| Bundle lifecycle E2E | `powershell -File scripts/tests/bundle-lifecycle.test.ps1` | PASS (build → extract → 9 dirs → 13 agents → 33 skills → hooks/profiles/manifest) |
| Lifecycle start | `powershell -File scripts/start-demo.ps1` | PASS — "Public demo ready" |
| Lifecycle stop | `powershell -File scripts/stop-demo.ps1` | PASS — ports 8080/4173 released |
| TODO/TBD scan | apps,packages,e2e excluding `TODO(INTERNAL)` | NONE |
| Credential scan | apps,packages,e2e,docs,central | NONE |

## Commits in this milestone

- `e9ea025` docs: add M5 bundle integration implementation plan
- `bb14487` feat(m5): add hooks manifest and MCP role profiles
- `35037a0` fix(m5): align profile servers with the mcp catalog and pin hook events
- `79f9395` feat(m5): ship hooks and profiles with the bundle and activate hook settings
- `5b2bf7e` fix(m5): preserve user hook settings, validate events, and remove stale installer hooks
- `6e6f1a5` feat(m5): add a no-Docker checksummed bundle build script
- `4220317` chore: ignore transient pnpm tmp artifacts
- `333874e` test(m5): map evals to tests and pin the review-pr residual-risks contract
- `2aa124a` docs(m5): correct the import-pod-members eval pins to the actual confirmation tests
- `d6c2a32` test(m5): add the build-extract bundle lifecycle E2E

## Environment quirks observed (not code defects)

1. The M1–M4 quirks still apply: run Playwright suites as separate invocations; never pipe `start-demo.ps1`/`stop-demo.ps1`.
2. `scripts/build-bundle.ps1` relaxes `$ErrorActionPreference` to `Continue` around the pnpm contracts-validation call: pnpm 10 prints a `pnpm.overrides` deprecation WARN on stderr, which PowerShell 5.1 otherwise wraps as a terminating `NativeCommandError` even with `*> $null`. The `$LASTEXITCODE` gate still decides pass/fail.
3. Hook settings are activated as local deterministic echo no-ops (`echo <action> >/dev/null && exit 0`); real hook commands require company Copilot policy confirmation and platform-safe invocation — tracked as `INTERNAL-HOOKS-001`.
4. `pnpm` intermittently leaves empty `_tmp_<pid>_<hex>` files at the repo root during validation runs; `.gitignore` now covers `_tmp_*`.
5. The bundle ZIP is flat at the root (no wrapping folder); `Expand-Archive` yields `agents/`, `skills/`, … directly.

## Internal handoff status

- New internal configuration point registered in `docs/handoff/INTERNAL_TODO.md`: `INTERNAL-HOOKS-001` (confirm VS Code agent-hook policy; replace echo no-ops with approved deterministic commands).
- The bundle install surface (agents/skills/instructions via VS Code chat customization locations; hooks via `chat.agent.hooks`; content dirs shipped) is verified against the fake profile and mocked VS Code. Real Copilot discovery (`/skills`) remains internal verification.
