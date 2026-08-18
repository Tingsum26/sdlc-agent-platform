# Central Agent/Skill Catalog Rework Design

**Date:** 2026-08-18
**Status:** Approved for implementation planning
**Scope:** Rework the central Copilot customization bundle (agents, skills, instructions, policies, templates, evals, mcp catalog, manifests) using popular public open-source frameworks as references. This is the run-first plan's M5 deliverable, pulled forward by owner request. No backend/MCP/VSIX feature changes except a minimal, documented path-mapping adaptation in `bundleInstaller.ts` if required.

## 1. Goals

1. Replace the current partial bundle (3 agents, 4 skills, 2 policies) with the complete catalog from the seven-repository spec: 13 agents, 27 skills, 19 instructions, 15 policies, 19 templates, evals, mcp catalog, and bundle manifest.
2. Every file's structure and content is traceable to named public open-source references.
3. Format stays within the VS Code GitHub Copilot-supported intersection (no Claude-Code-only fields/commands).
4. License compliance: only MIT / Apache-2.0 sources are copied or closely adapted; no-license repositories are concept-only references and are never copied.

## 2. Reference map (fixed in `REFERENCES.md`)

| Area | Reference | License | What we take |
|---|---|---|---|
| Skill format + frontmatter | [agentskills/agentskills](https://github.com/agentskills/agentskills) | Apache-2.0 | SKILL.md layout, description-matching principles, resources layout |
| Skill format baseline | [obra/superpowers](https://github.com/obra/superpowers) | MIT | SKILL.md structure and RED/GREEN contract-eval pattern already used in this repo |
| SDD stage boundaries | [github/spec-kit](https://github.com/github/spec-kit) | MIT | requirement → spec → plan → task → archive phase boundaries |
| Spec lifecycle | [Fission-AI/OpenSpec](https://github.com/Fission-AI/OpenSpec) | MIT | spec directory lifecycle concept (proposal/apply/archive) |
| Copilot agent file fields | [github/awesome-copilot](https://github.com/github/awesome-copilot) | MIT | VS Code `.agent.md` fields (name/description/model/tools/handoffs) |
| SDLC agent roster | [arozumenko/sdlc-skills](https://github.com/arozumenko/sdlc-skills) | MIT | Role division across the SDLC, Copilot-compatible wording |
| Engineering skill content | [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills) | MIT | Review/test/TDD skill step breakdown and checklists |
| Full-stack skill steps | [Jeffallan/claude-skills](https://github.com/Jeffallan/claude-skills) | MIT | Skill step decomposition per topic |
| Copilot-compatible SDD harness | [gotalab/cc-sdd](https://github.com/gotalab/cc-sdd) | MIT | Skills usable by Copilot (concept + structure) |
| Frontend engineering checklists | [vercel-labs/agent-skills](https://github.com/vercel-labs/agent-skills) | no license file | CONCEPT ONLY — no text copied |
| Agent Skills examples | [anthropics/skills](https://github.com/anthropics/skills) | no license file | CONCEPT ONLY — no text copied |
| Skill catalogs | [ComposioHQ/awesome-claude-skills](https://github.com/ComposioHQ/awesome-claude-skills), [VoltAgent/awesome-agent-skills](https://github.com/VoltAgent/awesome-agent-skills) | no license / MIT | Catalog organization ideas; VoltAgent MIT only for structure |

Compliance rules:
- Only MIT and Apache-2.0 sources may be copied or closely adapted; `REFERENCES.md` records repo, stars (as of 2026-08-18), license SPDX, and which files drew on it.
- No-license sources appear in `REFERENCES.md` under a "Concept-only (not copied)" section.
- All content remains fictitious-data-only and carries the existing `TODO(INTERNAL)`/`example.invalid` conventions where applicable.

## 3. Target directory layout

```text
central/
├─ agents/                       # 13 .agent.md files
├─ skills/
│  ├─ workflow/                  # start-epic, join-epic, change-epic, start-ticket*, resume-workflow*, import-pod-members*
│  ├─ analysis/                  # analyze-code-context, grill-requirement, assess-api-compatibility
│  ├─ design/                    # design-solution, plan-change, adr
│  ├─ implement/                 # implement-task, java-development, web-development, ios-development, android-development
│  ├─ test/                      # generate-tests, plan-manual-e2e, record-manual-e2e, review-accessibility, review-analytics-tagging
│  ├─ review/                    # prepare-pr*, review-pr
│  ├─ onboard/                   # onboard-repository, onboard-journey, sync-onboarding, analyze-http-call-graph
│  └─ sm/                        # analyze-epic-risk, prepare-standup, find-blockers, check-release-readiness, draft-jira-update
├─ instructions/                 # 19 .instructions.md files
├─ policies/                     # 15 policies (2 existing reworked)
├─ templates/                    # 19 templates
├─ evals/                        # at least 1 behavioral scenario per agent and per core skill
├─ mcp/catalog.json              # reworked from current
├─ manifests/bundle-manifest.json
└─ REFERENCES.md
```

`*` = existing file migrated and reworked into the new location and format.

## 4. Agents (13)

1. `epic-delivery-analyst` — epic intake, ticket matrix, journey gap analysis (read-only tools + workflow MCP)
2. `delivery-coordinator` — Scrum Master view: stand-up, blockers, release readiness (read-only + workflow MCP)
3. `requirement-analyst` — grills requirements with code evidence (reworked existing)
4. `code-context-analyst` — AS-IS evidence pack from code/Onboarding (read-only)
5. `solution-architect` — cross-repo design, API compatibility, ADRs (reworked existing)
6. `planner` — implementation plan and repo task breakdown
7. `java-implementer` — Java/Spring implementation under repo instructions
8. `web-implementer` — web implementation with accessibility baseline
9. `ios-implementer` — iOS implementation
10. `android-implementer` — Android implementation
11. `test-designer` — automated + manual E2E test generation
12. `accessibility-qa` — WCAG 2.2 AA + VoiceOver/TalkBack checks
13. `pr-reviewer` — read-only structured review findings; `model` uses a review-preference fallback chain (reworked existing)

Agent file conventions (Copilot-supported intersection):
- frontmatter: `name`, `description`, `model` (only for pr-reviewer as a fallback chain), `tools` (only local MCP tools + read/search tools), `handoffs` (workflow gate order).
- No Claude-only `allowed-tools`/permission syntax, no Copilot CLI assumptions.

## 5. Skills (27)

Each `SKILL.md` frontmatter: `name`, `description` (written for model matching), `version` (repo-custom; ignored by Copilot, used by bundle validation). Body: **When to use / Procedure (steps + hard gates) / Output contract (schema + acceptance)**. Skills may carry `references/` and `assets/`.

Skill ↔ agent ownership and the workflow gate chain follow the run-first spec (M1–M7). `grill-requirement` keeps the Socratic single-question pattern; `import-pod-members` keeps DRY_RUN→confirm→APPLY.

## 6. Instructions (19)

general, security-privacy, evidence, design-standards, java, web, ios, android, hybrid-journey, api-design-compatibility, native-release-flag, architecture, automated-testing, manual-e2e, accessibility, analytics-tagging, logging-observability, documentation-onboarding, jira-traceability.

Each: mandatory rules only; no model invocation; company-specific values stay out (TODO(INTERNAL) where an internal default belongs).

## 7. Policies (15)

stage-gates, stage-skip, api-backward-compatibility, native-release-compatibility, feature-flag, artifact-evidence, artifact-freshness, privacy-redaction, manual-e2e, accessibility, analytics-tagging, jira-projection, pod-assignment, review-model-routing, public-fixture-safety. Rework existing `stage-gates-v1.json` and `api-compatibility-v1.json` into the new structure; keep machine-readable shape (JSON) so the existing policy loader keeps working or adapts minimally.

## 8. Templates (19)

epic-intake, requirement-contract, solution-design, api-contract, cross-platform-design, implementation-plan, repo-onboarding, journey-onboarding, manual-e2e, accessibility, tagging, pr-description, pr-review, standup, risk, release-readiness, jira-comment, internal-agent-handoff, internal-completion-report.

## 9. Evals

- One behavioral scenario per agent (expected role behavior, wrong-role refusal, no-fabricated-evidence).
- One contract test per skill (frontmatter presence, version, output contract mention) in `packages/contracts` plus at least one RED/GREEN-style scenario for the workflow-critical skills (`start-epic`, `grill-requirement`, `review-pr`, `import-pod-members`).

## 10. Bundle integration

- `manifests/bundle-manifest.json` lists every file; existing VSIX `bundleInstaller` path logic must install the new `central/` layout. If `bundleInstaller` hardcodes old paths, adapt minimally and record it in the commit.
- `packages/contracts/test/central-bundle.test.ts` and `customizations.test.ts` extended to assert: 13 agents present, 27 skills present with valid frontmatter, agent↔skill references resolvable, `REFERENCES.md` exists with license column, no Claude-only fields anywhere.

## 11. Verification gates

1. `pnpm --filter @sdlc/contracts test` green (extended bundle assertions).
2. `pnpm test` and `pnpm build` green repo-wide.
3. `pnpm e2e:public-mvp` (bundle path sanity via existing VSIX flows where applicable).
4. Static scan: no `TODO`/`TBD` outside `TODO(INTERNAL)`; no credentials.
5. Commit per logical group with test evidence.

## 12. Out of scope

- No backend/MCP/VSIX feature changes (bundle path mapping exception documented above).
- No copying from no-license repositories.
- No changes to the workflow state machines or REST surface.

## 13. Self-review notes

- Closed list of agents/skills/instructions/policies/templates; each maps to the run-first spec.
- Reference map has license column; compliance rule is explicit.
- Copilot-format intersection is explicit (fields listed).
- Scope is bounded to the central bundle plus contract tests.
