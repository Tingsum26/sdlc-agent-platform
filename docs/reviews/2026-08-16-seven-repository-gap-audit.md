# Seven-Repository Gap Audit

**Date:** 2026-08-16  
**Target:** `docs/superpowers/specs/2026-08-16-seven-repository-platform-design.md`  
**Baseline:** current `agent/mvp-vertical-slice` monorepo

## Decision

The existing implementation is a verified public vertical slice, not a complete implementation of the approved SDLC platform. Repository splitting alone will not close the gaps; the destination repositories must implement and test the missing behaviors listed below.

## Inventory correction

| Area | Current evidence | Approved target | Correct status |
|---|---|---|---|
| Agents | 3: Requirement Analyst, Solution Architect, PR Reviewer | 13 defined Agents | PARTIAL |
| Skills | start-ticket, resume-workflow, prepare-pr, importing-pod-members | Full Epic, Ticket, onboarding, QA, Scrum and review catalog | PARTIAL |
| Instructions | global, Java Spring, Web | Java/Reactor/Web/iOS/Android/hybrid/design/test/security/accessibility/tagging/observability/review | PARTIAL |
| Policies | API compatibility and stage gates | Full compatibility, skip, evidence, freshness, privacy, QA, routing and projection policies | PARTIAL |
| Evals | Pod import-oriented coverage | Per-core-Agent/Skill positive, negative, degraded and safety cases | PARTIAL |
| Workflow Service | Ticket slice, task/artifact/audit, identity/Pod examples, adapters, Journey analyzer | Epic/Ticket/Repo Task, revisions, skip, freshness, Scrum, projection and complete artifact model | PARTIAL |
| Local MCP | 12 bounded tools for the slice | Complete workflow/onboarding/context/QA/diagnostic catalog | PARTIAL |
| VSIX | 9 contributed view IDs, mostly generic task providers | 9 semantically distinct view models and actions | PARTIAL |
| Contracts | monorepo JSON/TypeScript/Java shapes | Independent OpenAPI/events/generated packages/versioning | PARTIAL |
| Web/E2E | fictional vertical-slice demo | Dedicated reference demo with cross-channel Epic flow | PARTIAL |
| Logging | useful service/MCP/VSIX diagnostics contract | Preserve and extend across split repositories | PARTIAL |

## Required migration disposition

| Current location | Destination |
|---|---|
| `apps/workflow-service` | `sdlc-workflow-service` |
| `apps/workflow-mcp` | `sdlc-workflow-mcp` |
| `apps/vscode-extension` | `sdlc-vscode-workbench` |
| `.github/agents`, `.github/skills`, `.github/instructions`, `skills`, `mcp`, `policies`, `evals`, `manifests` | `sdlc-copilot-customizations` |
| `packages/contracts` plus shared Java contract types | `sdlc-workflow-contracts` |
| `apps/web-ui`, `packages/ui`, `e2e` | `sdlc-reference-demo` |
| architecture, ADRs, BOM, roadmap, handoff and cross-repository verification | `sdlc-agent-platform` |

No source is deleted from the monorepo until its destination builds, tests, packages, and is referenced by the platform BOM.

