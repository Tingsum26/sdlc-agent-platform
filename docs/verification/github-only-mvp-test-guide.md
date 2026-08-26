# GitHub-only MVP test guide

This path requires only GitHub, VS Code Copilot, Git and Node.js. It does not
start Workflow Service, MongoDB, Docker, Jenkins or a cloud Agent.

## Journey repository setup

Clone the central bundle branch and copy its Journey templates/scripts into a
private Journey repository:

```powershell
git clone -b feat/github-journey-context-gates https://github.com/Tingsum26/sdlc-copilot-customizations.git .sdlc-kit
```

Copy `templates/journey-workflow.json` to `.sdlc/workflow.json`, copy both
`scripts/*journey*.mjs` files to `scripts/`, and copy
`templates/verify-journey.yml` to `.github/workflows/verify-journey.yml`.
Create the baseline files declared by the template:

```text
docs/01-context/journey-baseline.md
docs/01-context/code-context.md
```

Set both baseline statuses to `APPROVED` only after human review. Keep private
source code and internal URLs in the private Journey repository.

## Select the Journey repository first

Start `delivery-coordinator` before `requirement-analyst`. Tell it which private
GitHub/GitHub Enterprise repository is the Journey repository, for example
`journey-account-opening`, and which local workspace path is checked out. The
Coordinator records this in `.sdlc/workflow.json` as
`journeyRepository.status: CONFIGURED`. Do not select an API, Web, iOS or
Android code repository for this purpose. No Agent output is allowed before
this selection is confirmed.

## Complete Journey onboarding before Start Epic

Journey onboarding is a separate reusable PR, not the first output of an
Epic. On the Journey repository's onboarding/default branch, select
`code-context-analyst` and use `onboard-journey`. It creates the approved
baseline, repository landscape, API call graph, code context and
`.sdlc/journey-onboarding.json`. Run `onboard-repository` for every API/Web/
iOS/Android repository that the Journey may use; record its approved source
commit in the Journey onboarding manifest.

Do not select `epic-delivery-analyst` to infer HTTP/API relationships. That
Agent only consumes the approved technical graph to split tickets, identify
business/release dependencies and surface risks.

When starting an Epic, `delivery-coordinator` runs this internal preflight:

```powershell
node scripts/check-journey-onboarding.mjs --repositories account-opening-api,account-opening-web
```

The user does not run it manually. If it returns `BLOCKED_BY_ONBOARDING`, the
Coordinator names the missing baseline artifact or repository and routes to
the relevant onboarding Skill. It must not create an Epic branch/PR or a new
baseline to bypass the gate.

## Start and hand off an Agent

```powershell
git switch -c journey/AO-123-open-account
```

In Copilot Chat select `requirement-analyst` and ask it to follow the GitHub
Journey collaboration instruction. It must automatically run the internal
`prepare-stage-context` Skill, read the generated Context Receipt and all
listed inputs, use `start-ticket` and `grill-requirement`, and write only the
requirement artifact with `appliedSkills` and the receipt hash. The user does
not run the Node command.

Validate before opening the PR:

```powershell
node scripts/verify-journey-artifact.mjs --stage REQUIREMENTS --artifact docs/02-requirements/requirement-contract.md
git add .
git commit -m "docs: add AO-123 requirement contract"
git push -u origin journey/AO-123-open-account
```

The PR check rejects missing, stale or incomplete receipts and missing required
Skills. A human must approve the artifact before its status becomes `APPROVED`.

## Exercise the forced hand-off gate

The specialist does not start the next role after writing its Markdown. Set
the output artifact status to `PENDING_APPROVAL` and verify that
`advance-stage` refuses to proceed. After a human reviews the PR, set the
artifact status to `APPROVED` (or ask `delivery-coordinator` to record
`SKIPPED_WITH_EVIDENCE` with actor, reason, evidence and risk), commit it, and
ask the Coordinator to resume. The Coordinator records an explicit human
approval, invokes the internal `advance-stage` Skill and prepares the next
role's Context Receipt. The user does not edit JSON, run the Node command or
choose an arbitrary target stage.

Create or update the one Journey PR after the validated artifact commit. Its
description must link the report, show the current `PENDING_APPROVAL` decision,
and recommend `delivery-coordinator` plus `/resume-workflow AO-123`. Its
marked Agent report comment must show the same handoff and inline the report
when it fits the configured comment size. The GitHub PR is the required shared
human UI. All Agent outputs remain shared Markdown in the Journey repository;
the Context Receipt is the mandatory, hash-pinned subset for the next stage,
not a second chat-memory channel.

## Resume and test the GitHub PR workbench

After a restart, open the Journey repository and select `delivery-coordinator`
in Copilot Chat, then run `/resume-workflow AO-123`. It reads GitHub files and
the latest PR instead of old chat history, reports the human gate and suggested
next specialist Agent, and never performs AI work automatically.

Confirm in GitHub that a reviewer without VSIX can open the linked Markdown
report, inspect its commit diff, read the marked report comment and see the
same `delivery-coordinator` / `/resume-workflow AO-123` handoff. After human
approval, ask the Coordinator to resume; it alone advances the declared order
and routes the next specialist.

## Optional VSIX companion

Build the GitHub-only VSIX:

```powershell
git clone -b feat/github-only-mvp-workbench https://github.com/Tingsum26/sdlc-vscode-workbench.git
cd sdlc-vscode-workbench
pnpm install
pnpm package
```

Install `dist/sdlc-workbench.vsix` and open the Journey folder. The eight views
show stage, artifacts, linked repositories, receipt health and the Coordinator
command. MCP Center reports that Workflow MCP is not required. Changes under
`.sdlc/` and `docs/` refresh the views.

Click an artifact row (for example the requirement contract or a surface map)
to open the local HTML Agent Report. Confirm that the report header shows
`reportType`, stage, role, status, revision, evidence and Context Receipt
metadata, and that Markdown tables and code are readable. With the panel open,
edit the artifact or run `git pull`; the report should refresh automatically.
Change the committed workflow status and verify the status badge changes. If
the artifact is deleted, the panel must show a safe missing-artifact warning
instead of failing or rendering arbitrary files. Mermaid is intentionally
shown as source text in this MVP.

The MVP proof is: a second Agent can use the same branch, a changed upstream
document makes the receipt stale and fails the PR check, reports are reviewable
directly in GitHub without VSIX, and work resumes after closing VS Code without
MongoDB or Workflow Service.
