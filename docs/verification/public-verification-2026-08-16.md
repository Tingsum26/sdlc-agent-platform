# Public verification — 2026-08-16

## Outcome

PASS for the public, no-container Fake-profile MVP. This evidence does not certify company integrations, production security, or internal infrastructure.

## Automated verification

| Check | Result | Evidence |
|---|---|---|
| `pnpm install --frozen-lockfile` | PASS | all 6 workspace projects, lockfile current |
| `.\mvnw.cmd verify` | PASS | 37 unit + 12 integration = 49 Java tests |
| `pnpm lint` | PASS | all configured workspace lint scripts |
| `pnpm test` | PASS | 49 tests: Contracts 22, MCP 8, UI 8, VSIX 9, Web 2 |
| `pnpm build` | PASS | TypeScript packages, VSIX bundle and production Web bundle |
| `pnpm e2e:public-mvp` | PASS | 1 Playwright Chromium vertical-slice scenario |
| `pnpm exec playwright test e2e/internal-shaped-simulation.spec.ts` | PASS | 1 identity/Pod/adapter/Journey/HTML scenario |
| `.\scripts\tests\stop-demo.test.ps1` | PASS | 1 isolated UTC/PID lifecycle regression |
| `pnpm --filter sdlc-workbench package` | PASS | installable `dist/sdlc-workbench.vsix` generated |
| `pnpm audit --audit-level low` | PASS | no known npm vulnerabilities after safe dependency upgrades |

Automated scenario count: 101 (49 Java + 49 Node + 2 browser E2E + 1 PowerShell lifecycle).

## Runtime verification

The public launcher started the Fake-profile Workflow Service and Web UI. `/actuator/health` returned `UP`, the Web UI returned HTTP 200, the stop script terminated both owned process trees, and ports 8080/4173 were released.

During verification, the UTC/PID protection test exposed a PowerShell `DateTime` double-conversion defect. A reproducing regression was added first, the conversion was corrected, and both the isolated test and real start/stop cycle then passed.

## Static review

- `git diff --check`: PASS.
- Production secret-pattern scan: no findings. A synthetic credential URI exists only in a negative configuration-contract test.
- No unfinished-work markers in delivery sources and documents.
- No Docker, local MongoDB, embedded database, Testcontainers, MinIO, S3, or cloud-agent runtime path is required. The Mongo contract deliberately rejects local/container addresses.
- No VS Code Language Model API call appears in the VSIX; Copilot interaction remains explicitly user-initiated.
- Secret, real-domain and evidence-vocabulary scans passed; the known fictional loopback webhook secret is intentionally excluded from the production-secret pattern.
- LLM Wiki is documented as post-MVP in the README, design, plan, review, and delivery manifest.

## Toolchain fingerprint

- Java: 17.0.8
- Node.js: 24.15.0 (project floor: 20.19.0)
- pnpm: 10.34.1
- Spring Boot: 3.5.16
- MCP TypeScript SDK: 1.30.0

## Internal-only validation

Company MongoDB connectivity, GHES/Jira/Confluence/Jenkins/Splunk adapter proof, authentication/SSO, signed bundle distribution, reviewer model availability, real Journey onboarding, native release compatibility, security approval, and production scale remain assigned to the internal Agent. It must follow `docs/handoff/INTERNAL_CONNECTION_GUIDE.md` and return only the redacted completion report.
