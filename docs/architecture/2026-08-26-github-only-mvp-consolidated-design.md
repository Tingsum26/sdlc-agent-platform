# GitHub-only multi-Agent SDLC MVP — consolidated design

**Status:** Accepted for MVP
**Updated:** 2026-08-26
**Audience:** engineering, architecture, QA, scrum masters, delivery leads
**Canonical state:** private GitHub Journey repository and its pull request

## 1. Executive decision

Build the first usable version as a **GitHub-only, local-Copilot workflow**.
All AI reasoning is started manually in VS Code GitHub Copilot Chat. GitHub is
the durable shared context and audit trail. A private Journey repository holds
the workflow state and the Markdown outputs written by the Agents; each code
repository continues to own its code and code PRs.

The MVP deliberately has **no Workflow Service, MongoDB, Workflow MCP,
Docker, cloud Agent, server-side model, S3 dependency, or Jenkins model
execution**. These are possible later scaling components, not prerequisites.

The three retained workflows are:

1. **Onboarding / Sync** — establish and refresh reusable Journey and code
   context before delivery starts.
2. **Journey / Epic Delivery** — turn an Epic or approved material change
   into a cross-channel scope, dependency and release plan.
3. **Ticket SDLC** — take an API, Web, iOS or Android ticket through
   requirements, design, plan, implementation evidence, test evidence and
   review.

## 2. Goals and non-goals

| In scope for MVP | Explicitly out of scope for MVP |
|---|---|
| Share reviewed Agent outputs across people, machines and Copilot sessions | Shared hidden Copilot chat memory |
| Make the next valid Agent/stage obvious and enforceable | Autonomous/background Copilot execution |
| Make GitHub PR the shared human report workbench | A custom web workflow portal |
| Support Java/Spring Boot/Rx, Web, iOS, Android and hybrid Journeys | Production graph database or cross-Journey query service |
| Generate reviewable requirements, design, test and manual E2E guidance | Claiming that generated tests passed without evidence |
| Preserve optional VSIX report UI and next-step guidance | VSIX as a model caller, workflow server, or approval engine |

## 3. Repository, branch and artifact model

Each real business Journey has **one private Journey repository**. It is not
an API/Web/mobile code repository. It contains the business and technical
coordination evidence for that Journey.

```text
Journey repository: account-opening-journey
  onboarding/default branch
    .sdlc/journey-onboarding.json
    docs/01-context/             approved reusable baseline

  journey/AO-123-address-change  one shared change branch
    .sdlc/workflow.json          current state, stage, gates, PR links
    .sdlc/context-receipts/      version-pinned inputs per stage
    docs/02-requirements/
    docs/03-design/
    docs/04-plan/
    docs/05-test/
    docs/06-review/

Code repositories: api-customer | web-onboarding | ios-app | android-app
  ticket/AO-124-...              normal code branches and code PRs
```

Use the Journey branch convention:

```text
journey/<epic-or-change-id>-<short-slug>
```

Do not use a channel name such as `api` as the shared Journey branch. A single
active Journey PR represents the shared delivery record; individual code PRs
remain in their own repositories and are linked from the Journey state.

The Journey repository may start as a lightweight repository containing only
the standard folders, workflow configuration and reports. It does **not**
duplicate source code. It records code repository URLs, relevant commits,
symbols, contracts and PR links.

## 4. What makes this genuine multi-Agent collaboration

An **Agent** is a role: it has an owner boundary, inputs, output, stop
conditions and approval expectation. A **Skill** is a reusable procedure that
an Agent is allowed or required to follow. A **Prompt File** is a versioned,
manually invoked entry interaction that collects only current inputs and binds
to the intended Agent; it cannot replace the Agent's rules or bypass a gate.
The Agent/Skill relationship is many-to-many; central routing is the policy
source.

The shared context is not a Copilot conversation. It is committed Markdown,
workflow state and Context Receipts in Git. Every later Agent can recover it
after a reboot, a new machine or a new Copilot session.

```text
approved upstream Markdown + workflow.json
                 ↓
prepare Context Receipt (paths + SHA-256 hashes + required Skills)
                 ↓
specialist reads its receipt and writes one typed Markdown output
                 ↓
deterministic validation + Journey PR review
                 ↓
human approves, or records SKIPPED_WITH_EVIDENCE
                 ↓
delivery-coordinator advances exactly one declared next stage
```

A receipt proves that the required, version-pinned inputs were supplied. It
does not prove that a model understood them semantically; human review remains
the semantic and business-quality control.

## 5. Agent ownership and Skills

`delivery-coordinator` is the default **main Agent**. It creates or resumes a
Journey workflow, validates gates, prepares the next Context Receipt and
routes to a specialist. It does not replace specialist analysis or silently
advance a stage.

| Need | Agent that owns the result | Important Skills / boundary |
|---|---|---|
| Choose Journey repo, recover state, route work | `delivery-coordinator` | `start-epic`, `resume-workflow`, `advance-stage`; no technical graph inference |
| Cross-repository API/HTTP and hybrid relationship baseline | `code-context-analyst` | Sole owner of `onboard-journey`, `onboard-repository`, `analyze-code-context`, `analyze-http-call-graph` |
| Turn poor Jira intake into a reviewable contract | `requirement-analyst` | `grill-requirement`; Socratic questions plus Jira and code/onboarding evidence |
| Architecture and compatibility design | `solution-architect` | contract-first design; may be skipped only with recorded human evidence |
| Delivery plan and tickets | `planner` | implementation plan, dependency order and ownership |
| Cross-ticket Epic delivery/risk plan | `epic-delivery-analyst` | Consumes approved technical graph; never creates/changes technical call edges |
| API / Web / iOS / Android changes | respective channel implementer | Writes code only in the channel code repo; links evidence back to Journey |
| Automated, manual E2E, accessibility and tagging strategy | `test-designer` | Distinguishes proposed tests from executed evidence |
| Diff and evidence review | `pr-reviewer` | Dedicated review model may be configured later; no self-approval |

The central customization package is the reusable catalog of Agents, Skills,
Prompt Files, instructions, schemas, policies, report templates, hooks and
evals. It ships 14 Prompt Files for the MVP entry points, including onboarding,
Epic start/resume, requirements, design, planning, four implementation
channels, test/accessibility and PR review. Teams do not redefine an Agent per
repository unless their business rule truly differs; they configure
Journey/repository facts in the Journey repository instead.

## 6. Onboarding is a separate dependency, not an Epic stage

Onboarding happens on the Journey repository's onboarding/default branch and
is reviewed and merged **before** an Epic branch is created. Its required
outputs are:

- `.sdlc/journey-onboarding.json` with approval and source-commit pins;
- `docs/01-context/journey-baseline.md`;
- `repository-landscape.md` (repositories and channels involved);
- `api-call-graph.md` (HTTP/API/async relationships); and
- `code-context.md` (entry points, contracts, constraints and known gaps).

`code-context-analyst` is the one technical owner of this work. Every call
edge must carry repository/channel, caller/callee, HTTP method/endpoint,
request/payload/header evidence where known, WebView/hybrid boundary,
feature-flag/release observations, source commit, file/symbol and a label:
`CODE_PROVEN`, `UNVERIFIED`, or `KNOWN_GAP`.

The Agent first builds a commit-pinned repository map, assesses context
freshness, and traces client/server contracts from both sides. `CODE_PROVEN`
requires caller and callee source evidence (or a checked generated/OpenAPI
contract); a route or client found on only one side remains an endpoint/client
fact, not a Journey edge. Optional local tools such as SCIP, CodeQL or Joern
may add provenance when already available, but no scanner, Docker, CI change,
server or code upload is an MVP dependency. Their output must agree with the
checked source and be recorded with tool/version/command/commit.

The `epic-delivery-analyst` may use the approved graph to make the delivery
DAG, ticket/channel matrix and risk register. It must return
`BLOCKED_BY_ONBOARDING` rather than invent an API relationship.

`start-epic` internally runs `check-journey-onboarding` against the named
repositories and their currently selected immutable commits. Missing,
unapproved, unpinned or commit-mismatched evidence blocks the start and states
exactly what must be refreshed. A failing check must not create the Epic
branch, workflow file, baseline or PR.

## 7. The three workflows

### 7.1 Onboarding / Sync

Use `code-context-analyst` for cross-repository relationship discovery. Start
with the Journey repository and all available API/Web/iOS/Android repository
references. If a repository is unavailable, record the limitation; do not
pretend the graph is complete.

Refresh onboarding manually when a relevant repository commit, contract or
dependency changes. This is a human-started Copilot task, not an automatic
server job in MVP.

### 7.2 Journey / Epic Delivery

Start with `delivery-coordinator` and `/start-epic <epic-or-change-id>` in
Copilot Chat. The coordinator first confirms the Journey repository, performs
the onboarding gate and creates the shared Journey branch only after it passes.

An Epic may originate from Jira or from a material urgent change entered by a
human. The input must state the business outcome, change reason, affected
Journey and known urgency. It does not bypass evidence or approval gates.

The approved Epic output captures business objective, scope, Requirement
Contract, cross-channel/API contract links, compatibility policy, dependency
DAG, native release-train concern, feature flags, risks, ticket matrix and
overall E2E scope. It is coordination state, not a replacement for individual
ticket work.

### 7.3 Ticket SDLC

For a ticket under the Epic, start `requirement-analyst`. A poor high-level
Jira ticket is an input, not a complete specification. The Agent combines
Jira, approved Journey onboarding and relevant code context; it uses
Socratic questions to separate `AS_IS`, `TO_BE`, assumptions and unknowns.

The normal path is:

```text
Requirements → Design → Plan → Implement → Test → Review
```

The architecture/design stage may be skipped only when a human records
`SKIPPED_WITH_EVIDENCE` with actor, reason, evidence and accepted risk. The
Coordinator cannot silently skip a stage or jump to an arbitrary later stage.

## 8. Context gate, approval gate and recovery

Every specialist begins with a stage-specific Context Receipt generated inside
the Agent/Skill workflow. Users should not be asked to run
`prepare-journey-context.mjs` manually. The receipt contains artifact IDs,
paths, hashes and the required Skill route. The Agent embeds the receipt path
and hash and `appliedSkills` in its output.

Each result starts as `PENDING_APPROVAL`. A human uses the Journey PR to
approve it, request changes, or declare `SKIPPED_WITH_EVIDENCE`. The
Coordinator records that decision through the deterministic
`record-human-decision` step; an approval records actor/evidence/timestamp and
a skip additionally requires reason and accepted risk. Only then is the
coordinator allowed to run `advance-stage`, which follows `stageOrder` and
selects the next declared Agent. Missing, stale, blocked or unapproved inputs
stop the workflow.

To resume after closing VS Code, switching laptops or pausing for days:

1. Clone or pull the Journey repository and shared Journey branch.
2. Start `delivery-coordinator` and use `/resume-workflow <workflowId>`.
3. The coordinator reads `.sdlc/workflow.json`, receipts, approvals and PR
   links, then names the next permitted Agent and prompt.

This makes the history recoverable without dependence on any Copilot session.

## 9. GitHub PR: required shared report workbench

GitHub PR is the required shared human interface. It works for developers,
architects, QA, BA and scrum masters who have repository access; it does not
require VSIX installation.

One active Journey PR is maintained for each Journey change. After a
specialist completes a report, it:

1. validates the artifact and leaves it `PENDING_APPROVAL`;
2. commits only its output, receipt and workflow metadata to the unprotected
   Journey branch;
3. pushes and creates/updates the single Journey PR;
4. upserts a marked comment (`sdlc-agent-report:<workflowId>:<artifactId>`)
   containing the report summary, decision required, next Agent after approval
   and `/resume-workflow <workflowId>`; and
5. records PR number, URL and comment URL in `.sdlc/workflow.json`.

The committed Markdown is canonical. The PR body and comment are projections
for review. Inline full comments are limited to 45,000 characters; larger
reports link to the committed artifact. No Agent merges, approves, force-pushes
or writes to a protected branch.

GitHub Markdown supports tables, diffs and native Mermaid where the GitHub
Enterprise version/configuration permits it. Do not rely on arbitrary raw
HTML/CSS/JavaScript in PR comments. The standalone HTML report is for sharing
outside the PR, not the PR's canonical rendering format.

## 10. VSIX: optional local report companion

Keep the VSIX, but do not make it a workflow dependency. The extension reads
the checked-out `.sdlc/workflow.json` and Markdown and provides:

- structured HTML report rendering with a common report schema;
- immediate next-Agent and recommended Copilot prompt;
- current stage, receipts, freshness, linked code repositories and PRs;
- local refresh when the report, workflow file, Git pull or coordinator state
  changes; and
- My Work, Scrum Master, Epic, Ticket, Repo Task, Customization Center, MCP
  Center and Diagnostics views.

It never calls a model, holds canonical workflow state, makes a semantic
approval, advances a stage or receives a webhook in the MVP. GitHub PR remains
the team-shared report surface. VSIX uses safe Markdown rendering, escaped
untrusted fields, restricted links and source-only Mermaid handling.

## 11. Compatibility, hybrid delivery and QA

For Account Opening, use the agreed release rule: Web and API can be released
together or ahead of native; native follows the shared release train. Any
other Journey must confirm its release rule during onboarding.

- API contracts are non-breaking by default: add optional fields/endpoints,
  preserve existing behavior and use a version/adapter when a breaking change
  is unavoidable.
- API/Web must remain compatible with released native clients.
- Native adoption can use an approved feature toggle (for example the existing
  AWS toggle capability); flag ownership, rollout, rollback and removal date
  are part of the design and test evidence.
- Hybrid outputs must map Web/mobile screen or WebView boundary to API,
  method, request payload, common header, response use, error mapping and
  rollout order.
- Test reports include generated automated test proposals **and** manual E2E
  scenarios that cover what automation cannot prove. They include expected
  result, prerequisites, data, steps, observability and rollback checks.
- QA design includes accessibility (keyboard/screen reader/contrast where
  relevant) and analytics/tagging events, attributes and verification.

An Agent must label unexecuted tests as proposed. CI/Jenkins/GitHub checks may
run deterministic builds, tests and receipt validation, but have no model and
must not be described as an Agent.

## 12. Integrations, identity, logging and safety

All company systems are intranet systems. Local MCPs may be used by the person
running Copilot for Jira, Confluence, GitHub Enterprise, Figma and later code
graph tooling. They are connectors, not a workflow database and not cloud
MCPs. `gh` or VS Code source control can be the local GitHub publishing
fallback. MCP authentication and availability must be verified in the intranet
before production use.

MVP authorisation is intentionally simple: GitHub repository/PR permissions
are the practical access control; all authorised collaborators may start a
workflow. Git commit/PR/comment actors supply the audit trail. SSO, Teambook
pod membership/role synchronisation, TL permissions and automatic assignment
are deferred. Do not store employee directories or personal data in the
central public package.

Git commits, pull requests and CI results are the durable MVP audit log. The
VSIX keeps local diagnostic logs. Never log credentials, tokens, source
snippets containing sensitive customer data, raw prompts or hidden model
reasoning. A later service can forward structured operational events to Splunk
when an API service exists.

## 13. Recommended first-use path

1. Install/update the central customisation package in VS Code and clone the
   Journey repository.
2. Select `code-context-analyst` and run Journey Onboarding; review and merge
   the baseline PR.
3. Select `delivery-coordinator`, run `/start-epic <id>`, and let it validate
   onboarding before creating the Journey branch.
4. For each channel ticket, select `requirement-analyst`. Review its Journey
   PR report, approve it, then return to the coordinator to route the next
   stage.
5. Specialists create their own reports and code PR evidence. Each report is
   visible in the same Journey PR and, locally, in the optional VSIX.
6. At any pause, use `delivery-coordinator` plus `/resume-workflow <id>`.

## 14. MVP acceptance checks

- A Journey cannot start an Epic with missing or stale required onboarding.
- The only Agent that derives technical cross-repository HTTP/API edges is
  `code-context-analyst`.
- Every specialist output has a valid, current Context Receipt and declared
  required Skills.
- Every report is committed and discoverable in the active Journey PR, with a
  clear human decision and next route.
- A closed/reopened local environment reconstructs state from Git alone.
- A design skip is explicit, attributed and risk-recorded.
- Ticket workflows enforce `Requirements → Design → Plan → Implement → Test
  → Review`; Test and Review both consume verified implementation evidence and
  linked code-PR evidence.
- Test output distinguishes proposed, executed, passed, failed and blocked.
- VSIX can display the same checked-out evidence without becoming mandatory.

## 15. Deliberately deferred expansion

Reconsider a Workflow Service, MongoDB, Workflow MCP and event-driven
automation when many teams need transactional assignment, real-time board
views, cross-Journey analytics, locking, webhook processing or fine-grained
role controls. A server-side MCP would normally run as a Node.js stdio/HTTP
process (or equivalent hosted runtime) alongside a durable service; it offers
central orchestration but violates the current local-Copilot-only MVP
constraint unless it remains deterministic and model-free.

Also deferred: LLM Wiki ingestion/retrieval, S3 report storage, managed graph
aggregation, automatic Jira projection, SSO/Teambook integration, dynamic
role permissions, central Scrum board service and cloud-hosted MCPs. Evaluate
these after the GitHub-only pilot has demonstrated where Git's limits are real.

## 16. Intranet handoff boundary

The public implementation can provide contracts, instructions, templates and
deterministic checks. An internal Agent must validate actual GitHub Enterprise,
Jira, Confluence, Figma, VS Code/Copilot, Jenkins and local MCP connectivity.
It must return a **redacted report, not company source code**, covering
available tools, permissions, tested commands, evidence, failures and
recommended configuration changes. Those findings are then reviewed before
claiming the workflow is ready for internal production use.
