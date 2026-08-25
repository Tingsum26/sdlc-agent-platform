# ADR: GitHub-only multi-Agent collaboration for MVP

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

Only a developer manually starting GitHub Copilot Agent in VS Code performs AI
reasoning. Local MCPs are optional read/write connectors for Jira, Confluence,
GitHub Enterprise, Figma and code-graph tools; they do not persist workflow
state. The VSIX is a UI for the checked-out workspace, reports, PR/check
status and next-step prompts; it does not invoke a model or become a workflow
server.

### VSIX report renderer boundary

The VSIX is also the local human-facing **Agent Report Workbench** for
presentation-heavy outputs. Agents remain responsible for producing canonical
Markdown artifacts in the Journey repository; each artifact carries the
central report front matter (`reportType`, `stage`, `role`, `status`, revision,
evidence level and Context Receipt hash). The VSIX renders that artifact into a
consistent HTML view with metadata, tables, code and Mermaid source blocks,
including reports such as requirements, page/API surface maps, design,
testing and review.

An open report watches the artifact and `.sdlc/workflow.json`, so a Copilot
commit, pull or Coordinator status change is reflected without reopening the
view. This is a presentation and review boundary only: the VSIX does not call
an LLM, decide whether content is correct, approve an artifact, advance a
stage, or replace the GitHub PR gate. The renderer escapes untrusted Markdown,
restricts links, prevents artifact path escape and keeps Mermaid as safe source
text in MVP; graphical diagram rendering can be added later as a trusted local
enhancement.

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

The Coordinator, every specialist Agent and the VSIX read the same committed
`.sdlc/workflow.json` and Journey Markdown. They therefore show the same
`currentStage`, gate state and `nextAgent`; this is a file-based protocol, not
shared Copilot chat memory. All committed Markdown is discoverable shared
context. A Context Receipt makes only the stage-relevant upstream documents
mandatory, with exact hashes, to avoid forcing every Agent to load an
oversized or stale document set. The output remains available to all later
Agents and people through the Journey branch.

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
- The VSIX clearly shows the checked-out branch, last commit, current stage,
  receipt freshness, linked PRs and `LIVE`/`STALE` status.
- Clicking an artifact in My Work, Scrum Master, Epic or Ticket views opens a
  local HTML Agent Report with its type, stage, role, evidence and receipt
  metadata, and the open report refreshes after a file pull/edit or workflow
  status update.
