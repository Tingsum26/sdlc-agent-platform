# M8 Milestone Verification (2026-08-21)

Branch: `agent/mvp-vertical-slice`
Milestone: M8 — machine-validated `TODO(INTERNAL)` registry and an updated
public handoff boundary. This evidence also re-runs the public-run gates after
the M1–M7 remediation work.

## Scope

M8 adds a JSON registry, Markdown parity validation, a Node CLI, tests, and a
GitHub Actions gate for every canonical internal configuration marker in
`apps`, `packages`, and `central`. It does **not** make company integrations
available from this public repository.

## Fresh gates

All commands were run on Windows PowerShell from
`D:\\codex\\sdlc-agent-platform\\.worktrees\\agent-mvp-vertical-slice` on
2026-08-21. Playwright suites were run individually and ports 8080 and 4173
were confirmed free before each invocation.

| Gate | Command | Result |
|---|---|---|
| Java full suite | `./mvnw.cmd -q verify` | PASS — 41 Surefire reports, 163 tests, 0 failures, 0 errors, 0 skipped |
| Frozen dependency install | `pnpm install --frozen-lockfile` | PASS — lockfile current |
| Node tests | `pnpm test` | PASS — 119 tests: contracts 35, Workflow MCP 12, VSIX 58, shared UI 8, Web UI 6 |
| Node builds | `pnpm build` | PASS — all five runnable workspaces built |
| Node lint gate | `pnpm lint` | PASS — no workspace supplies a failing lint script |
| Dependency advisory scan | `pnpm audit --audit-level low` | PASS — no known vulnerabilities at this run |
| Public browser vertical slice | `pnpm e2e:public-mvp` | PASS — 1/1 |
| M1 browser E2E | `pnpm e2e:m1` | PASS — 1/1 |
| M2 browser E2E | `pnpm e2e:m2` | PASS — 1/1 |
| M3 browser E2E | `pnpm e2e:m3` | PASS — 1/1 |
| M4 browser E2E | `pnpm e2e:m4` | PASS — 1/1 |
| M7 browser E2E | `pnpm e2e:m7` | PASS — 1/1 |
| Central bundle build | `powershell -File scripts/tests/build-bundle.test.ps1` | PASS |
| Central bundle lifecycle | `powershell -File scripts/tests/bundle-lifecycle.test.ps1` | PASS |
| Windows stop lifecycle | `powershell -File scripts/tests/stop-demo.test.ps1` | PASS — PID-reuse, process-tree, and discovery-failure cases |
| VSIX package | `pnpm --filter sdlc-workbench package` | PASS — typecheck/build/package; 6-file 18.01-KB VSIX |
| Registry unit tests | `node --test scripts/tests/internalTodoRegistry.test.mjs` | PASS — 11/11 |
| Registry CLI | `pnpm verify:internal-todos` | PASS — 10 IDs and 19 canonical source marker paths |
| Canonical marker scan | Exact PowerShell/ripgrep command below | PASS — 19 markers, agreeing with the registry |
| Credential-pattern scan | Exact PowerShell/ripgrep command below | PASS — 0 AWS/GitHub/Slack/private-key pattern matches |
| Demo lifecycle | `start-demo.ps1` → health/Web HTTP → `stop-demo.ps1` | PASS — health `UP`, Web `200`, then no listeners on 8080/4173 |

The two static scans were rerun after the document review with exactly these
commands. The first scans only public source roots and excludes generated,
documentation, and scanner-fixture paths. The second scans the full worktree
while excluding VCS, dependency, generated, and test-report paths.

```powershell
$markerPattern = 'TODO\(INTERNAL\):\s*INTERNAL-(?:[A-Z0-9]+-)*\d{3}'
$markers = @(rg -n --glob '!node_modules/**' --glob '!target/**' --glob '!dist/**' --glob '!docs/**' --glob '!scripts/tests/**' $markerPattern apps packages central)
Write-Host "canonical-marker-hits=$($markers.Count)"
if ($markers.Count -ne 19) { $markers; exit 1 }

$credentialPattern = '(AKIA[0-9A-Z]{16}|gh[pous]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----)'
$credentials = @(rg -n --hidden --glob '!.git/**' --glob '!node_modules/**' --glob '!target/**' --glob '!dist/**' --glob '!test-results/**' --glob '!playwright-report/**' $credentialPattern .)
Write-Host "credential-pattern-hits=$($credentials.Count)"
if ($credentials.Count -ne 0) { $credentials; exit 1 }
```

Observed result: `canonical-marker-hits=19`; `credential-pattern-hits=0`.

The pnpm commands emit a known configuration warning that the root
`package.json` `pnpm.overrides` field is no longer read by this pnpm version.
It did not cause a command failure and is recorded separately from gate
outcomes. The Java suite intentionally logs fake Splunk best-effort failure
scenarios while asserting the publisher does not fail the workflow.

## Relevant commits

- `e6e5cbe` — registry validator and CI gate
- `3e8420f` — duplicate Markdown registry ID detection
- `af89ef0`, `b10dbc8`, `e04bdfd`, `03b3d87` — Windows demo shutdown
  correctness and regression coverage
- `d63dd0f` — M3 E2E now establishes an approved artifact before requesting
  the server-derived Jira projection
- `3abc013` — public vertical-slice E2E follows the type-aware,
  approval-only requirement-analysis policy
- `397cb6e` — credential-redaction test fixture remains behaviorally covered
  without creating a scanner-shaped token literal
- `221ac26` — Windows shutdown follows descendants from a verified live root
  identity

## Explicit non-completion boundary

This is a successful public, fictional-data M8 verification gate; it is **not**
completion of the approved target platform.

- `INTERNAL-AUD-001` remains pending: M2/M3/M4 aggregate persistence must be
  connected to the managed company MongoDB and verified for restart/resume,
  TLS/authentication, indexes, backup/restore, retention, and performance.
- The approved seven-repository split remains pending. The verified artifact is
  still the run-first public monorepo; it has not yet become the seven separate
  repositories specified in the target design.
- Company SSO/GHES/Jira/Confluence/Jenkins/Splunk/Copilot policy, production
  release controls, real Journey onboarding, and their evidence remain internal
  work represented by the registered IDs and handoff materials.
- Public `SIMULATED_PASS` provenance is session-scoped: it identifies only a
  deterministic fictional run in the current fake-runtime process. It is not
  evidence of a human QA execution, a release, company connectivity, or
  provenance that survives/reconciles after a fake-runtime restart.
