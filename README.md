# Local Copilot SDLC Platform — Overview, BOM & Handoff

This repository is the **overview / documentation / BOM / handoff** hub of the
seven-public-repository Local Copilot SDLC platform. It intentionally contains
**no runtime code**: all runnable artifacts live in the six repositories below,
extracted from this repo at baseline tag `seven-repo-split-baseline`
(`bf48e15`) and verified independently before the split cleanup (M7).

## The seven repositories

| Repository | Role | Version | Commit at split |
|---|---|---|---|
| [`sdlc-agent-platform`](https://github.com/Tingsum26/sdlc-agent-platform) (this repo) | overview, docs, BOM, handoff | 0.8.0 | `bf48e15` |
| [`sdlc-workflow-contracts`](https://github.com/Tingsum26/sdlc-workflow-contracts) | versioned JSON Schemas + TypeScript contracts, `contracts.lock.json` | 0.1.0 | `38457cf` |
| [`sdlc-workflow-service`](https://github.com/Tingsum26/sdlc-workflow-service) | Spring Boot workflow state machine, audit, freshness, Jira projection; no model client, no container dependency | 0.1.0 | `f69352a` |
| [`sdlc-workflow-mcp`](https://github.com/Tingsum26/sdlc-workflow-mcp) | stdio Local Workflow MCP gateway (deterministic tools, safe errors, correlation IDs) | 0.1.0 | `f0bfa80` |
| [`sdlc-copilot-customizations`](https://github.com/Tingsum26/sdlc-copilot-customizations) | central Agents/Skills/Instructions/Policies/Evals/Templates/Hooks/manifests + guardrail tests | 0.1.0 | `a029cd2` |
| [`sdlc-vscode-workbench`](https://github.com/Tingsum26/sdlc-vscode-workbench) | UI-only VSIX workbench (no model API invoked), bundle install/rollback | 0.1.0 | `9f40c53` |
| [`sdlc-reference-demo`](https://github.com/Tingsum26/sdlc-reference-demo) | fictional cross-channel reference demo (Web UI + shared UI + Playwright E2E) | 0.1.0 | `68862aa` |

Compatibility rule: consumers pin `@sdlc` contracts by SemVer range;
breaking changes require a major bump and a migration note
(`contracts.lock.json` is maintained in `sdlc-workflow-contracts`).

## Verification status at split

Every destination repository was extracted from the verified monorepo state
(Draft PR #1 head `bf48e15`, full M8 gate matrix green on 2026-08-22:
Java 175 tests + 57 IT, Node 123 tests, 6 browser E2E suites, VSIX package,
registry 10 IDs / 19 markers, credential scan 0 hits), then re-verified
standalone:

- contracts: vitest green (15 tests) + `contracts.lock.json`
- service: `./mvnw verify` green — 123 unit + 57 failsafe IT, 0 failures
- mcp: vitest green (13 tests)
- copilot-customizations: guardrail ports green (20 tests)
- vscode-workbench: vitest green (61 tests) + packaged VSIX
- reference-demo: vitest green (14 tests) + production build

The machine-validated internal-TODO registry moved with its markers:
see `docs/handoff/internal-todo-relocation.md`.

## Hard boundaries

- No model client anywhere in runtime code; the human runs and supervises
  VS Code GitHub Copilot Chat.
- Public fixtures are fictional (`example.invalid`, `REPO_A`, `DEMO-123`).
- No Docker, Compose, local MongoDB, Testcontainers, MinIO/S3, cloud agents,
  or Jenkins modification required or permitted.
- Simulated outcomes are labelled `SIMULATED_PASS`, `CONTRACT_PASS`,
  `INTERNAL_VALIDATION_REQUIRED`, or `BLOCKED`; they never imply company proof.

## Documentation map

- Approved seven-repository target & DoD: `docs/superpowers/specs/2026-08-16-seven-repository-platform-design.md`
- Gap audit (PARTIAL areas vs target): `docs/reviews/2026-08-16-seven-repository-gap-audit.md`
- Migration mapping (M0): `docs/architecture/seven-repository-migration-map.md`
- Platform BOM: `docs/platform-bom.yaml`
- Delivery manifest & handoff templates: `docs/handoff/PUBLIC_DELIVERY_MANIFEST.md`,
  `docs/handoff/public-to-internal-handoff.md`,
  `docs/handoff/internal-agent-completion-report-template.md`,
  `docs/handoff/public-delivery-manifest-template.md`
- Verification evidence M1–M8: `docs/verification/`

## Known open items

- PARTIAL vs target design: 13-Agent catalog (3 exist), full MCP tool catalog,
  eight distinct VSIX view models, complete Journey onboarding — tracked in the
  gap audit and per-repo READMEs as post-split work.
- `INTERNAL-AUD-001`: connect aggregate persistence to managed company MongoDB
  during intranet adoption (intranet-only work).
