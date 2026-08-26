# Run-First Public Milestones Design

**Date:** 2026-08-18
**Status:** Approved for implementation planning
**Supersedes:** implementation sequencing in `2026-08-16-seven-repository-platform-design.md` §19 (migration order stays deferred until after this run-first phase)
**Depends on:** the approved seven-repository spec (`docs/superpowers/specs/2026-08-16-seven-repository-platform-design.md`) as the capability inventory.

## 1. Goal

Make the complete SDLC platform runnable end-to-end on a public VS Code installation using only fictitious data, before the seven-repository split and before any internal-network rollout. When the internal agent later takes over, it must only replace the environment (config, transport, credentials), not business logic.

## 2. Scope

### In scope

- Continue work in the current monorepo worktree, branch `agent/mvp-vertical-slice`.
- Eight runnable milestones M1–M8, each keeping the system startable.
- All capabilities listed in the seven-repository spec that are marked `PARTIAL` or `MISSING` for the following components:
  - Workflow Service (Java/Spring Boot).
  - Local Workflow MCP (stdio).
  - Central customization bundle (Agents/Skills/Instructions/Policies/Templates/Evals/Hooks).
  - VSIX workbench (8 views).
  - Reference demo (Web UI + browser E2E).
- Public fake environment only: `fake` Spring profile, in-memory repositories, `DeterministicFakeTransport`, fictitious identities and `example.invalid` data.

### Out of scope (deferred, unchanged from prior specs)

- Seven-repository split.
- Public URL deployment (owner selected option A: local one-command run).
- Real internal systems (MongoDB, Jira, Confluence, GHES, Jenkins, Splunk, SSO, Teambook).
- LLM Wiki, S3, GridFS, Docker, server-side agents, RBAC enforcement.
- Any new model invocation: inference stays in user-started Copilot Chat only.

## 3. Environment contract

### `fake` profile guarantees

- All repositories in-memory (`InMemory*` implementations already present, extended as needed).
- No MongoDB, no S3, no GridFS, no Docker at any milestone.
- Identity: fictitious non-GitHub Scrum Master `EMP-100` plus fictional Pod members from the sample CSV.
- All external adapters use `DeterministicFakeTransport` with configurable latency/failure for tests.

### One-command run

- `scripts/start-demo.ps1`: starts Workflow Service (`fake` profile, executable JAR) and the Web demo, verifies health `UP` and Web `200`, prints the URLs and a "what to click next" summary.
- `scripts/stop-demo.ps1`: stops both processes and verifies ports are released.
- These scripts are regression-tested (process lifecycle test already exists; extended per milestone).

## 4. Common acceptance criteria per milestone

Every milestone must satisfy all of:

1. `start-demo.ps1` brings the stack up; health `UP`; Web returns `200`.
2. Milestone-specific Playwright E2E is green.
3. Component-specific gates: MCP stdio protocol tests green when MCP changes; VSIX `typecheck`, `build`, `package` green when VSIX changes.
4. `stop-demo.ps1` releases ports.
5. Static scan: no real credentials, no unresolved `TODO`/`TBD` other than registered `TODO(INTERNAL)` markers.
6. Commit per milestone with test evidence in the commit body.

## 5. Milestones

### M1 — Identity and Pod routing (runnable)

Deliver fictitious `Principal`, non-GitHub Scrum Master binding (`EMP-100`), Pod CSV import (`DRY_RUN`/`APPLY`/report), and deterministic Ticket-to-Pod assignment.

- Existing seed: `ImportService` tests and `importing-pod-members` Skill.
- Gap: assignment engine, `DirectoryPerson` non-onboarded states, enrollment-code stub for non-GitHub identities.
- E2E: import fictitious Pod CSV → list members → create Ticket → verify Pod queue + suggested assignees.

### M2 — Three-level workflow (runnable)

Deliver Epic → Ticket → Repo Task state machines, manual emergency change request, skip attestation, dependency DAG, resume-from-shutdown semantics.

- Gap: Epic and Repo Task layers are spec-only today; change request and skip flows are spec-only.
- E2E: create Epic → attach API/Web/iOS/Android Tickets → Repo Tasks → emergency change → skip one stage with attestation → verify audit trail and resume.

### M3 — Enterprise adapters (runnable)

Deliver Jira, Confluence, GHES, Jenkins, Splunk adapters on the unified transport with timeout/retry/idempotency/error classification, plus the Jira projection outbox (summary comments, `JIRA_SYNC_PENDING`, retry).

- Existing seed: `*Adapter` + `DeterministicFakeTransport` for diagnostics.
- Gap: Jira projection queue, Jenkins CI status flow, Splunk structured event emission from the service, redaction tests.
- E2E: complete a stage → Jira comment draft → confirm publish → simulated Jenkins CI status visible in Ticket View.

### M4 — Journey/Repo Onboarding (runnable)

Deliver the Account Opening fictitious Journey manifest: repository list, Web/iOS/Android screens, HTTP call graph, request/response payload schemas, unified header, compatibility matrix, Native release train, Feature Flag plan, freshness (`LIVE`/`DELAYED`/`STALE`/`OFFLINE`), and the evidence-badged HTML Journey report.

- Existing seed: `journey` contract module and safe HTML renderer.
- Gap: freshness engine, coverage/gap computation, account-opening sample data, report entry point in Web demo.
- E2E: open Journey report → verify evidence badges → mark one repo stale → refresh flow.

### M5 — Complete central customization bundle (runnable)

Deliver all 13 Agents, the full Skill catalog (start/join/change Epic, start-ticket, resume, code-context, grill-requirement, design, plan, implement, generate-tests, prepare-pr, review-pr, onboarding, HTTP call graph, Pod import, manual-E2E, accessibility, tagging, API compatibility, SM five-command chain), Instructions, Policies, Templates, Evals, Hooks manifest, MCP profiles, and bundle build/install/rollback.

- Existing seed: 3 Agents, 4 Skills, 2 Policies, 2 Evals, 1 manifest.
- Gap: the other 10 Agents, the remaining Skills, Instructions set, Templates, Hooks, Evals, bundle tooling.
- E2E: build bundle → install through VSIX Customization Center → `/skills` discovery list complete → rollback to previous version.

### M6 — VSIX 8 independent views (runnable)

Deliver My Work (landing), Scrum Master, Epic, Ticket Detail with nested Repo Task Detail, Identity/Pod Configuration, Customization Center, MCP Center, Diagnostics — each with its own view model, refresh/freshness semantics, and tests. No shared generic task tree.

- Existing seed: view IDs exist but share `taskTreeProvider`.
- Gap: view models, distinct data loading, freshness badges, offline/error states, accessibility.
- E2E: each view renders its own data; Ticket Detail nests Repo Task; Diagnostics shows MCP/Service health.

### M7 — End-to-end browser E2E (runnable)

Deliver the full fictional SDLC path in the Web demo: `/start-epic` → requirement analysis → design → plan → implement → generate tests → manual E2E → PR preparation → review → CI evidence, with a complete audit trail and HTML reports at each stage.

- E2E: full path green; audit history lists every stage event; reports open with evidence.

### M8 — `TODO(INTERNAL)` registry and handoff (runnable)

Deliver the `TODO(INTERNAL)` marker convention, the `docs/handoff/INTERNAL_TODO.md` registry with stable IDs, a CI check that every marker is registered, and the updated internal-agent completion report template requiring per-ID status.

- E2E: CI fails when an unregistered marker exists; handoff docs list every internal configuration point with rollback notes.

## 6. Testing strategy

- TDD per milestone: failing contract/unit/E2E test first, then implementation.
- Contracts stay the single source of truth for MCP/VSIX/Service payloads.
- Full verification before final commit: all Java tests, all Node tests, browser E2Es, PowerShell lifecycle test, dependency audit, static boundary scan.

## 7. Risks

- M5 catalog size: mitigated by template-driven generation where safe, but each Agent/Skill must still have at least one behavioral Eval.
- VS Code/Copilot behavior differences remain `TODO(INTERNAL)`; public side verifies bundle structure, Skill discovery metadata, and MCP stdio only.
- Keeping each milestone runnable constrains commit size; accepted in exchange for owner-verifiable progress.

## 8. Self-review notes

- No placeholders: milestone list is closed (M1–M8), each with a green E2E definition.
- No contradictions with prior specs: the 8-view VSIX model (from the v2 design re-review) is used; Developer View stays removed; Repo Task Detail stays nested.
- Scope is bounded to one implementation plan; the seven-repository migration remains a separate future plan.
