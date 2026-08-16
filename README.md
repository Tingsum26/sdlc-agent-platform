# Local Copilot SDLC Platform

A public, no-container MVP for a human-controlled software delivery workflow whose only AI reasoning runs in an interactive VS Code GitHub Copilot session.

## What is implemented

- Java 17 / Spring Boot Workflow Service with state/audit/artifacts, Mongo document adapters, enterprise identity/Pod assignment, bounded Jira/Confluence/GHES/Jenkins/Splunk adapters, cross-repository Journey analysis, safe HTML reports, and Fake public runtime.
- Stateless Local Workflow MCP with twelve bounded tools, explicit Pod-import confirmation, cancellation, correlation IDs, safe errors, and structured stderr diagnostics.
- Central Copilot Agents, Skills, always-on/file-scoped instructions, schemas, policies, MCP catalog, evals, and a versioned bundle manifest.
- VSIX workbench with Developer, Scrum Master, My Work, Epic, Ticket, Repo Task, Customization, MCP, and Diagnostics views; task freshness polling; exact-version approval; safe HTML reports; bundle install/rollback; and no model API.
- Shared accessible React report components and a public Web UI validated with UI/UX Pro Max guidance.
- Browser E2E for both `DEMO-123 → MCP → report → approval → mock CI → manual E2E` and the fictional `EPIC-DEMO-1 → identity → Pod → assignment → five adapters → Journey HTML` flow.

## Hard boundaries

- Workflow Service, MCP, VSIX, Web UI, tests, adapters, Jenkins/GitHub CI, and persistence contain no model client. The user starts and supervises Copilot Chat.
- Public fixtures use `example.invalid`, `REPO_A`, `DEMO-123`, and fictional identities.
- No Docker, Compose, local MongoDB, embedded database, Testcontainers, MinIO, S3, cloud agent, or Jenkins modification is required.
- Public integration outcomes are labelled `SIMULATED_PASS`, `CONTRACT_PASS`, `INTERNAL_VALIDATION_REQUIRED`, or `BLOCKED`; simulated/contract results never imply company proof.
- Company MongoDB, GHES, Jira, Confluence, Jenkins, Splunk, SSO, Teambook, real repositories/Journeys, and reviewer model availability require internal validation.
- LLM Wiki, embeddings/vector search, cross-repository Journey onboarding, deterministic code-graph experimentation, and team-scale deployment are post-MVP work.

## Prerequisites

Java 17, Node.js 20.19 or newer, pnpm 10, VS Code 1.100 or newer, and GitHub Copilot Agent mode permitted by enterprise policy.

## Verify

```powershell
.\mvnw.cmd verify
pnpm install --frozen-lockfile
pnpm lint
pnpm test
pnpm build
pnpm audit --audit-level low
.\scripts\tests\stop-demo.test.ps1
pnpm e2e:public-mvp
pnpm exec playwright test e2e/internal-shaped-simulation.spec.ts
pnpm --filter sdlc-workbench package
```

The VSIX is generated at `apps/vscode-extension/dist/sdlc-workbench.vsix` and intentionally ignored by Git. Install it from VS Code with **Extensions: Install from VSIX**.

## Run the public demo

```powershell
.\scripts\start-demo.ps1
```

Open `http://127.0.0.1:4173`. Stop with `.\scripts\stop-demo.ps1`. See `docs/demo/public-mvp-walkthrough.md`.

## Use with Copilot

1. Build `@sdlc/workflow-mcp` and review `.vscode/mcp.example.json` before creating `.vscode/mcp.json`.
2. In the VSIX Customization Center, install an extracted reviewed bundle root and verify Chat Customizations diagnostics. Installation is explicit and retains a last-known-good rollback.
3. Open Copilot Chat, select **Requirement Analyst**, and invoke `/start-ticket DEMO-123`. On restart, use `/resume-workflow <task-id>`; server state and artifacts restore the next step.
4. Use the VSIX for results, exact-version human confirmation, status, MCP health, and logs—not AI inference.

## Documents

- Human-readable design: `docs/superpowers/specs/2026-08-15-local-copilot-sdlc-platform-v2-design.html`
- Pre-implementation review: `docs/reviews/2026-08-16-pre-implementation-review.md`
- Public delivery manifest: `docs/handoff/PUBLIC_DELIVERY_MANIFEST.md`
- Internal Agent handoff: `docs/handoff/INTERNAL_AGENT_HANDOFF.md`
- Logging contract: `docs/operations/logging-and-diagnostics.md`
- Implementation inventory: `docs/implementation/internal-shaped-implementation-inventory.md`
- Contract reference: `docs/reference/internal-shaped-contract-reference.md`
- Internal connection guide: `docs/handoff/INTERNAL_CONNECTION_GUIDE.md`
