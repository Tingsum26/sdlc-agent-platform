# ADR: GitHub-only multi-Agent collaboration for MVP

> **Current reading path:** this ADR records the decision. For the complete,
> shareable implementation model, workflow rules, role boundaries and MVP
> scope, use [the 2026-08-26 consolidated design](2026-08-26-github-only-mvp-consolidated-design.md)
> and its [HTML report](../reports/2026-08-26-github-only-mvp-design-report.html).

**Status:** Accepted for MVP on 2026-08-25  
**Supersedes for MVP:** the MongoDB / Workflow Service / Workflow MCP runtime
path in the earlier platform design. Those components remain Phase 2 options;
they are not deleted from the wider programme.

## Decision

The MVP persists workflow state in one private GitHub Journey repository per
Journey. A change uses a single shared branch:

```text
journey/<epic-or-change-id>-<slug>
```

The Journey branch contains `.sdlc/workflow.json`, Markdown artifacts, Context
Receipts, approvals and links to code-repository PRs. API/Web/iOS/Android code
continues to live in its own repository and uses its own ticket-based code
branch. A channel name such as `api` alone is never the shared Journey branch.

Journey repository selection is the first gate. The main `delivery-coordinator`
must ask for or confirm the private Journey repository and write its URL,
owner/name, local path and branch into `.sdlc/workflow.json` before any Agent
output is created. It must not confuse this with an API/Web/Mobile code
repository. Context preparation is an internal prerequisite Skill, not a user command.
When the user starts the Coordinator or a specialist Agent, that Agent (or the
Coordinator on behalf of the next specialist) automatically creates or
validates the Context Receipt. The user is interrupted only when required
evidence is absent or unapproved.

The three retained workflows are protocols over that repository:

1. **Journey / Epic:** intake, repository context, cross-channel contract,
   dependency and release strategy.
2. **Ticket SDLC:** requirement, design, plan, implementation evidence, test
   evidence and review, linked to the Journey branch and code PRs.
3. **Onboarding / Sync:** versioned Journey/repository baseline and a manual
   re-analysis when code or dependency evidence becomes stale.

Onboarding / Sync is a separate, reusable dependency of Journey / Epic. It is
completed on the Journey repository's onboarding/default branch and merged
before an Epic branch exists. `.sdlc/journey-onboarding.json` records approved
Journey baseline, repository landscape, API call graph, code context and an
approved, source-commit-pinned entry for each repository. `start-epic` first
runs `check-journey-onboarding.mjs` for the repositories named by the Epic. A
missing, unapproved or stale entry returns `BLOCKED_BY_ONBOARDING`, names the
specific repository/artifact to create or refresh, and does **not** create an
Epic branch, workflow file or PR. Only a passing check permits the new Epic
branch to inherit and reference the onboarding baseline.

`code-context-analyst` is the sole technical owner of Journey onboarding and
the cross-repository HTTP/API graph. It uses `onboard-journey`,
`onboard-repository` and `analyze-http-call-graph`, producing code-proven
repository/channel, caller/callee, endpoint, payload/header and source-commit
evidence. `epic-delivery-analyst` consumes that approved graph only to create
the business delivery dependency DAG, ticket/channel matrix and risk register;
it returns `BLOCKED_BY_ONBOARDING` rather than inventing or editing a technical
call edge.

Only a developer manually starting GitHub Copilot Agent in VS Code performs AI
reasoning. Local MCPs are optional read/write connectors for Jira, Confluence,
GitHub Enterprise, Figma and code-graph tools; they do not persist workflow
state. The Journey GitHub PR is the required shared human UI: its description
indexes committed reports, and each specialist upserts a marked report comment
with the immediate approval decision, next Agent after approval and
`/resume-workflow <workflowId>`. The VSIX is an optional UI for the checked-out
workspace, reports, PR/check status and next-step prompts; it does not invoke a
model or become a workflow server.

### GitHub-first report workbench and optional VSIX

The Journey GitHub PR is the shared **Agent Report Workbench** for MVP. Agents
produce canonical Markdown artifacts in the Journey repository; each carries
the central report front matter (`reportType`, `stage`, `role`, `status`,
revision, evidence level and Context Receipt hash). After deterministic
validation, the Agent commits/pushes its artifact to the unprotected Journey
branch, creates or updates the one Journey PR, and upserts a report comment.
GitHub renders the committed Markdown, tables, native code diffs and, when the
enterprise version/configuration supports it, Mermaid diagrams. The committed
file remains canonical; the PR description and comment are review projections.

The VSIX remains the optional local **Agent Report Workbench** companion. Its
eight views, report rendering, next-Agent guidance and suggested command are
retained. An open local report watches the artifact and `.sdlc/workflow.json`,
so a Copilot commit, pull or Coordinator status change is reflected without
reopening the view. Neither GitHub presentation nor VSIX calls an LLM, decides
whether content is correct, approves an artifact, advances a stage, or replaces
the GitHub PR gate. VSIX escapes untrusted Markdown, restricts links and keeps
Mermaid as safe source text in MVP.

An **Agent** is a role with ownership, boundaries, input/output and stop
conditions. A **Skill** is a reusable procedure. Their relationship is
many-to-many: the central `agent-skill-routing.json` declares required and
allowed Skills for each Agent. The existing `delivery-coordinator` Agent is
the default main Journey Coordinator: it creates/resumes the Journey, checks
gates and routes to specialists; it does not replace the requirement,
architecture, implementation, test or review Agents. A human still starts each
Agent in Copilot. The artifact records
`appliedSkills` and the Context Receipt records the stage route; the PR check
verifies declared Skill use, while human review evaluates whether the method
was applied correctly.

## Sequential gates and shared Markdown context

The MVP has a hard, deterministic hand-off gate between stages. A specialist
Agent writes only its declared Markdown output and leaves that artifact in
`PENDING_APPROVAL`. The human reviews the commit/PR and either approves it or
records `SKIPPED_WITH_EVIDENCE` with an actor, reason and accepted risk. The
main `delivery-coordinator` then invokes `advance-stage`, which checks the
output status and advances only to the next item in `stageOrder`. It does not
accept a user-supplied target stage, so Requirements cannot jump directly to
Plan. If the output is missing, stale, unapproved or blocked, the gate stops
and the next Agent is not started.

The Coordinator, every specialist Agent, the Journey PR and optional VSIX read
the same committed `.sdlc/workflow.json` and Journey Markdown. They therefore
show the same `currentStage`, gate state and `nextAgent`; this is a file-based
protocol, not shared Copilot chat memory. All committed Markdown is
discoverable shared context. A Context Receipt makes only the stage-relevant
upstream documents mandatory, with exact hashes, to avoid forcing every Agent
to load an oversized or stale document set. The output remains available to all
later Agents and people through the Journey branch and PR.

## Context handoff and enforcement

Markdown alone is not a safe multi-Agent protocol. The MVP requires a
deterministic **Context Receipt** for every stage:

```text
workflow.json stage declaration
       ↓
prepare-journey-context.mjs
       ↓
Context Receipt: upstream artifact IDs + paths + SHA-256 hashes
       ↓
Agent reads every listed input and writes one stage artifact
       ↓
Artifact front matter embeds receipt path + receipt hash
       ↓
verify-journey-artifact.mjs + GitHub PR check
       ↓
merge permitted only when the receipt is current
```

The scripts reject missing, unapproved or stale upstream inputs. A stage may
be skipped only as `SKIPPED_WITH_EVIDENCE`, recording actor, reason and
accepted risk. GitHub cannot prove that a language model semantically
understood a document; the receipt proves the much narrower, auditable fact
that the required version-pinned inputs and required Skill route were supplied.
Human PR review remains the control for semantic quality.

## Why this is multi-Agent collaboration rather than multiple chats

Each Agent owns one typed output and may begin only when its declared input
artifacts are available. It does not pass hidden chat history to the next
Agent. The source of truth is versioned and shared through Git:

| Stage | Owner | Required shared context | Output |
|---|---|---|---|
| Context | Code Context Analyst | Journey baseline, target repositories | Code context |
| Requirements | Requirement Analyst | Baseline + code context | Requirement contract |
| Design | Solution Architect | Baseline + code context + approved requirement | Solution design / API contract |
| Plan | Planner | Approved requirement + design | Implementation plan |
| Implement | Channel Implementer | Approved plan + linked code repo | Code PR + implementation evidence |
| Test | Test Designer / QA | Requirement + design + plan + implementation evidence | Automated/manual E2E evidence |
| Review | PR Reviewer | Above artifacts + actual diff and checks | Review report |

Parallelism is allowed only on separate artifacts or code repositories. Two
Agents must not write the same Markdown file or `workflow.json` at the same
time. For MVP, coordinate this simply with separate short-lived role branches
and GitHub PRs; the Journey owner merges them in dependency order.

## Consequences

This removes deployment and persistence complexity for the first usable
version, gives natural audit/recovery through commits and PRs, and works with
the local-only Copilot constraint. It does not provide transactions, real-time
assignment, cross-Journey queries, locks, event processing or fine-grained
role enforcement. Add Workflow Service/MongoDB later only when those limits
become material for team scale.

## MVP acceptance criteria

- A new Journey repository can be initialized from the central templates.
- Every non-baseline output references a validated Context Receipt.
- A PR check rejects a missing or stale receipt.
- A user can close VS Code, clone/pull the Journey branch elsewhere, run
  `resume-workflow`, and reconstruct the next action without chat history.
- Every verified output is visible in the active Journey PR through a canonical
  Markdown link and marked report comment containing the current approval gate,
  next Agent and `/resume-workflow` command.
- The optional VSIX clearly shows the checked-out branch, last commit, current
  stage, receipt freshness, linked PRs and `LIVE`/`STALE` status. Clicking an
  artifact opens a local HTML report that refreshes after a file pull/edit or
  workflow status update.
