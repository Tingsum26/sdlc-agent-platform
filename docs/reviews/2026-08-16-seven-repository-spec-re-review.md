# Seven-Repository Spec and Gap-Audit Re-Review

**Date:** 2026-08-16
**Baseline:** commit `76ffffa` on `agent/mvp-vertical-slice`
**Targets reviewed:**
- `docs/superpowers/specs/2026-08-16-seven-repository-platform-design.md`
- `docs/architecture/seven-repository-output-inventory.yaml`
- `docs/reviews/2026-08-16-seven-repository-gap-audit.md`
**Cross-checked against:** the approved v2 design (`docs/superpowers/specs/2026-08-15-local-copilot-sdlc-platform-v2-design.md`), the conversation decisions recorded through 2026-08-16, and the actual source tree.

## Verdict

The seven-repository spec is a faithful consolidation of the approved decisions, **except the VSIX view model**, which contradicts the final view-model decision recorded in the v2 design. The gap audit's numbers and migration disposition are accurate against the source tree. Two new owner requirements are not yet represented anywhere and must be injected into the implementation plan:

- **R1 — Public runnable milestone.** The owner wants to run the complete platform end-to-end on a public VS Code installation using only fictitious data before the internal rollout.
- **R2 — Internal-config TODO registry.** Every point that requires internal-network configuration must carry a `TODO(INTERNAL)` code comment and be collected in the internal handoff report.

## Finding 1 — VSIX view model contradiction (needs an owner decision)

Three artifacts disagree:

| Artifact | Views |
|---|---|
| v2 design (final approved decisions, lines 51, 564) | 8 views: My Work (landing), Scrum Master, Epic, Ticket Detail **with nested Repo Task Detail**, Identity/Pod Configuration, Customization, MCP, Diagnostics. No Developer View. |
| Seven-repo spec §14 | 9 views: Developer View, Scrum Master, My Work, Epic, Ticket, **top-level** Repo Task, Customization, MCP, Diagnostics. No Identity/Pod Configuration. |
| Code (`apps/vscode-extension/package.json` lines 35–43, `src/extension.ts` lines 17–18) | The spec's 9 IDs, all backed by the same generic `taskTreeProvider` (which the gap audit already flags). |

Conversation history shows Developer View was deliberately removed as a duplicate of My Work (2026-08-15 ~16:57 UTC), Repo Task Detail was nested under Ticket Detail, and Identity/Pod Configuration was added. The seven-repo spec silently reverted that decision.

**Recommendation:** restore the 8-view model (v2 design) and update the spec §5/§14, `seven-repository-output-inventory.yaml` (`VSIX-VIEW-001..009`), `package.json`, `extension.ts`, and the extension test. The plan must treat "distinct view models, not one generic task tree" as an explicit acceptance item (`VSIX-VIEW-*`).

## Finding 2 — No runnable-checkpoint milestones

Spec §19 defines migration order M0–M7 but never defines what "runnable" means, so nothing guarantees the owner can try the result at each step.

**Public Run Definition (to be written into the plan):**

- Workflow Service starts from an executable JAR with the `fake` profile (in-memory repositories, `DeterministicFakeTransport`, fictional identities such as `EMP-100 Fictional Scrum Master` and `example.invalid` data) — no MongoDB, no Docker, no GridFS/S3.
- Web demo serves on `127.0.0.1`, VSIX connects to `http://127.0.0.1:8080`, Local MCP runs over stdio, and the customization bundle is installed through the VSIX Customization Center.
- Copilot Chat drives `/start-epic` → analysis → design → implementation → tests → manual E2E → PR → review → CI evidence with fictitious data only.

The plan adds a **Public Developer Quick Start** (existing `scripts/start-demo.ps1`/`stop-demo.ps1` extended) plus a browser E2E as the acceptance gate of every milestone.

## Finding 3 — `TODO(INTERNAL)` convention is missing

Proposed convention (to be formalized in the plan and handoff):

- Marker: `// TODO(INTERNAL): <INTERNAL-XXX> <one-line action>` (Java/TS) and `# TODO(INTERNAL): <INTERNAL-XXX> ...` (PowerShell/YAML/Markdown).
- Every marker gets a stable ID registered in `docs/handoff/INTERNAL_TODO.md` in the `sdlc-agent-platform` repository, with: component, file, what to configure, evidence required from the internal agent, and rollback notes.
- The internal completion report template gains a mandatory section listing every `INTERNAL-XXX` ID with status (`DONE` / `BLOCKED` / `NOT_APPLICABLE`) and the redacted evidence.
- A CI check (platform repo) counts `TODO(INTERNAL)` markers per released file so a merged internal-config point cannot silently disappear from the registry.

## Finding 4 — Public-run identity path

Spec §11 defines production identity (GitHub Enterprise login + admin employee binding). A public VS Code run must work with no GHES at all. Verified in code: `FakeRuntimeConfiguration` (`@Profile("fake")`) provides in-memory identity, audit, artifact, Pod, assignment, and webhook stores plus a fictional non-GitHub Scrum Master binding (`EMP-100`). The plan keeps this as the public `dev` identity profile; production GHES/SSO/Teambook wiring stays `TODO(INTERNAL)`.

## Finding 5 — Contracts distribution for public local development

The spec requires generated Java DTO/client and TypeScript packages plus `contracts.lock.json`, but does not say how public developers consume them before any registry publishing exists.

**Recommendation for the plan:** the contracts repository builds both packages locally (`npm pack` / `mvn install` snapshots) for public development; GitHub Actions publish on tag (GitHub Packages / npm / Maven Central). Consumers pin exact contract versions via `contracts.lock.json`. No Docker, no private registry is required for the public run.

## Finding 6 — Gap audit verified against the tree

Checked against the worktree:

- 3 Agents (`.github/agents/`: pr-reviewer, requirement-analyst, solution-architect) vs 13 required.
- 4 Skills (`.github/skills/`: prepare-pr, resume-workflow, start-ticket; `skills/importing-pod-members`) vs the approved catalog.
- 2 Policies (`api-compatibility-v1.json`, `stage-gates-v1.json`), 2 Evals (Pod-import only), 1 bundle manifest, `mcp/catalog.json`.
- Migration disposition matches the tree: `apps/{workflow-service,workflow-mcp,vscode-extension}`, `apps/web-ui` + `packages/ui` + `e2e`, `packages/contracts`, `.github/*` + `skills/` + `mcp/` + `policies/` + `evals/` + `manifests/`.

The audit's `PARTIAL` corrections are accurate; passing tests prove the Ticket slice, not the platform.

## Owner decisions and resolution

- **Sequencing — A (run-first):** the complete platform is implemented and verified in the current worktree so the owner can run it end-to-end on public VS Code, then it is split into the seven public repositories following the spec's migration order.
- **View model — 8 views restored:** Developer View deleted, Repo Task Detail nested under Ticket View, Identity / Pod Configuration added. The spec §5/§14/§22, the inventory (`VSIX-VIEW-001..008`), and the machine-readable IDs are updated accordingly.
- **R1 and R2 injected:** the spec now defines the Public Run Definition and runnable checkpoints (§19) and the `TODO(INTERNAL)` convention and registry (§20). Inventory gained `PLATFORM-VERIFY-002` and `PLATFORM-HANDOFF-003`.

The remaining open question is purely scope-of-work in the implementation plan (which milestones come first), not a spec change.

## Next steps

Produce the per-repository implementation plan (each task keyed to `seven-repository-output-inventory.yaml` IDs) with run-first milestone ordering, the Public Run Definition, the `TODO(INTERNAL)` registry, per-milestone runnable checkpoints, and the updated handoff templates.
