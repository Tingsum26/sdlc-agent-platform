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

## Resume and test the VSIX

After a restart, open the Journey repository and select `delivery-coordinator`
in Copilot Chat, then run `/resume-workflow AO-123`. It reads GitHub files and
the latest PR instead of old chat history, chooses the next specialist Agent,
and never performs AI work automatically.

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

The MVP proof is: a second Agent can use the same branch, a changed upstream
document makes the receipt stale and fails the PR check, and work resumes after
closing VS Code without MongoDB or Workflow Service.
