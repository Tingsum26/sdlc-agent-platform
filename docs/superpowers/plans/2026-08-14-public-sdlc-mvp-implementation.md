# Public SDLC MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public vertical slice in which a user starts a mock Jira ticket, persists and approves workflow artifacts through Fake repositories, uses a standalone Local MCP, views HTML reports in VS Code, and sees existing CI status without any server-side AI or local infrastructure service.

**Architecture:** A Java/Spring Boot Workflow Service owns workflow state and structured artifact contracts behind repository ports. Public tests use Fake repositories; the internal deployment configures the company MongoDB through supplied YML and index manifests. Human HTML is rendered from structured records, and Jira summary/attachment publishing is an optional projection. A TypeScript MCP process and a VS Code extension are independent REST clients.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Maven Wrapper, Spring Data MongoDB configuration adapter, JUnit/Fake repositories, Node.js 20+, pnpm 10, TypeScript 5.9, React 19, Vite/Vitest, Playwright, VS Code Extension API, and Model Context Protocol TypeScript SDK v1.x. Exact patch versions are locked in build files; internal compatibility remains an explicit validation item.

## Global Constraints

- The only AI entry point is user-initiated GitHub Copilot Chat in local VS Code.
- Workflow Service, MongoDB, MCP, VSIX, mocks, and tests never call a model.
- VSIX must not use `vscode.lm`; Skills and MCP remain usable without VSIX.
- Do not add Jenkins plugins, scanners, Shared Libraries, or environment dependencies.
- Company MongoDB stores operational state and canonical structured artifacts; Jira receives summaries and optional approved attachments; Git stores reviewable documents.
- The public implementation starts no local database or object store and has no container, Embedded Mongo, MinIO, or Testcontainers dependency.
- Never include company code, URLs, credentials, repository names, API paths, users, or business data.
- Graph Scanner is excluded from this MVP.
- Phase 0 capabilities are assumed; use a fallback only after recording an actual failure.
- UI work must use the locally installed and validated `ui-ux-pro-max` Agent Skill or record the equivalent checklist fallback. Do not install or execute an unpinned UI generator package during the build.
- LLM Wiki, vector search, embedding generation, and model-assisted knowledge-graph enrichment are explicitly outside the MVP.
- Java API and business Web/iOS/Android repository profiles are follow-up plans, not this platform MVP.

---

## Locked File Structure

```text
pom.xml
package.json
pnpm-workspace.yaml
apps/
  workflow-service/
  workflow-mcp/
  vscode-extension/
  web-ui/
packages/
  contracts/
  ui/
mock-adapters/
e2e/
scripts/
docs/
```

The Java service is authoritative for workflow invariants. TypeScript clients consume JSON Schema/OpenAPI and do not duplicate transition rules.

---

### Task 1: Bootstrap the reproducible public monorepo

**Files:**
- Create: `pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Create: `tsconfig.base.json`
- Create: `eslint.config.mjs`
- Create: `.editorconfig`
- Create: `.gitignore`
- Create: `apps/workflow-service/pom.xml`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/WorkflowServiceApplication.java`
- Create: `apps/workflow-service/src/main/resources/application-mongodb.example.yml`
- Create: `apps/workflow-service/src/main/resources/application-fake.yml`
- Create: `apps/workflow-service/config/mongo-indexes.json`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/WorkflowServiceApplicationTest.java`

**Interfaces:**
- Produces `/actuator/health`, a Fake Repository profile for public tests, and a Mongo configuration contract using environment placeholders only.

- [ ] **Step 1: Initialize Git and write ignore rules**

Run `git init`. Ignore `.env`, IDE state, `node_modules`, `target`, `dist`, coverage, and local secrets; retain `.env.example` and `.vscode/mcp.example.json`.

- [ ] **Step 2: Write the failing Spring context test**

```java
@SpringBootTest
class WorkflowServiceApplicationTest {
  @Test void contextLoads() {}
}
```

- [ ] **Step 3: Verify it fails before scaffolding**

Run: `.\mvnw.cmd -pl apps/workflow-service test`
Expected: FAIL because wrapper/application files are incomplete.

- [ ] **Step 4: Add build files and infrastructure-free test profile**

Add Spring Data MongoDB configuration support, validation, actuator, JUnit, and a root Maven aggregator. Add no local database launcher. The public `fake` profile uses in-memory repository implementations; `application-mongodb.example.yml` contains placeholders such as `WORKFLOW_MONGODB_URI` and `WORKFLOW_MONGODB_DATABASE`, never credentials.

- [ ] **Step 5: Prove the scaffold without external dependencies**

```powershell
.\mvnw.cmd -pl apps/workflow-service test
```

Expected: context PASS under the Fake profile; Mongo configuration and index manifest contract tests PASS without opening a database connection.

- [ ] **Step 6: Commit**

```powershell
git add .
git commit -m "build: bootstrap public sdlc monorepo"
```

---

### Task 2: Define versioned contracts and the MongoDB workflow state machine

**Files:**
- Create: `packages/contracts/schemas/workflow-task-v1.schema.json`
- Create: `packages/contracts/schemas/approval-v1.schema.json`
- Create: `packages/contracts/schemas/artifact-v1.schema.json`
- Create: `packages/contracts/package.json`
- Create: `packages/contracts/src/types.ts`
- Create: `packages/contracts/test/schema.test.ts`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/TaskStatus.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/TaskType.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/WorkflowTask.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/WorkflowTaskRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/TaskTransitionPolicy.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/WorkflowTaskService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/AuditEvent.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/task/WorkflowTaskServiceTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/task/WorkflowTaskRepositoryContractTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/config/MongoConfigurationContractTest.java`

**Interfaces:**
- Produces `createTask`, `claimTask(taskId,userId,lease)`, `transition(taskId,expected,target,version)`, and `releaseExpiredLeases(now)`.
- Every mutable payload has `schemaVersion`, stable ID, timestamps, and optimistic `version`.

- [ ] **Step 1: Write failing JSON Schema tests**

Accept a valid `WAITING_FOR_LOCAL_COPILOT` task. Reject unknown statuses, missing scope, and secret-like fields named `token`, `password`, or `cookie`.

- [ ] **Step 2: Write failing transition tests**

Cover legal creation, claim, approval, CI, manual E2E, completion, cancellation, illegal completed-task restart, concurrent claim, expired lease reclaim, and stale version conflict.

- [ ] **Step 3: Run tests and confirm failure**

```powershell
pnpm --filter @sdlc/contracts test
.\mvnw.cmd -pl apps/workflow-service test
```

- [ ] **Step 4: Implement schemas, enums, policy, repositories, and audit writes**

Statuses are `CREATED`, `WAITING_FOR_LOCAL_COPILOT`, `LOCAL_COPILOT_RUNNING`, `WAITING_FOR_USER_CONFIRMATION`, `WAITING_FOR_APPROVAL`, `WAITING_FOR_CI`, `WAITING_FOR_MANUAL_E2E`, `BLOCKED`, `COMPLETED`, `CANCELLED`. Audit records contain actor, action, old/new status, version, time, and correlation ID but never prompt contents.

- [ ] **Step 5: Define Mongo indexes and run contract tests**

Define an index manifest for unique task ID; unique idempotency key; compound assignee/status/updatedAt; compound repositoryAlias/targetCommit/type. Validate the manifest and repository behavior against the Fake implementation. The internal Agent applies/verifies indices and runs real integration tests against an approved company non-production MongoDB.

- [ ] **Step 6: Commit**

```powershell
git add packages/contracts apps/workflow-service
git commit -m "feat: add versioned persisted workflow state"
```

---

### Task 3: Implement webhook, approval, CI-read, and structured artifact APIs

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/webhook/WebhookController.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/webhook/WebhookSignatureVerifier.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/webhook/WebhookDelivery.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/ScmEventAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/TicketAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/CiStatusAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/MockScmEventAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/MockTicketAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/MockCiStatusAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/ArtifactStore.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/FakeArtifactStore.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/MongoDocumentArtifactStore.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/JiraAttachmentPublisher.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/ArtifactMetadata.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/ArtifactService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/approval/ApprovalService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/security/CurrentUser.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/security/DemoHeaderAuthenticationFilter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/WorkflowTaskController.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/WorkflowApiIT.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/webhook/WebhookControllerIT.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/artifact/ArtifactServiceTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/artifact/JiraAttachmentPublisherTest.java`

**Interfaces:**
- REST: `POST /api/v1/workflows/from-ticket`, `GET /api/v1/tasks`, `POST /api/v1/tasks/{id}/claim`, `POST /api/v1/tasks/{id}/results`, `POST /api/v1/approvals`, `GET /api/v1/reports/{id}`.
- Webhook: raw-byte HMAC verification and unique delivery ID.
- Artifact: versioned structured sections, SHA-256 content hash, on-demand HTML rendering, Jira summary, and optional attachment projection.

- [ ] **Step 1: Write failing integration tests**

Cover valid/invalid HMAC, duplicate/unsupported events, `DEMO-123` workflow creation, unauthorized approval, stale version, read-only CI status, rejected executable content, hash mismatch, immutable approved report, Jira projection retry, and `LARGE_ARTIFACT_STORAGE_UNAVAILABLE`.

- [ ] **Step 2: Implement mock adapter ports and safe API errors**

Public fixtures use `example.invalid`, `REPO_A`, and fictional users. Errors use problem details with correlation ID and never expose exception text or request bodies.

- [ ] **Step 3: Implement structured Mongo contract, on-demand HTML, and Jira projection**

MongoDB stores canonical structured sections and metadata. Workflow Service renders HTML on demand. Jira comments store milestone summaries; an HTML/PDF attachment is optional and never authoritative. Approval requires role claim, artifact version, actor, timestamp, and decision. If no approved large-artifact provider exists, return an explicit capability status instead of starting local infrastructure.

- [ ] **Step 4: Run service verification**

Run: `.\mvnw.cmd -pl apps/workflow-service verify`
Expected: all service, Fake repository, configuration-contract, webhook, artifact, and Jira-projection tests PASS; OpenAPI snapshot generated. Real Mongo integration remains an internal validation item.

- [ ] **Step 5: Commit**

```powershell
git add apps/workflow-service mock-adapters packages/contracts
git commit -m "feat: expose secure workflow integration APIs"
```

---

### Task 4: Build the standalone Local MCP and Copilot customizations

**Files:**
- Create: `apps/workflow-mcp/package.json`
- Create: `apps/workflow-mcp/src/index.ts`
- Create: `apps/workflow-mcp/src/client.ts`
- Create: `apps/workflow-mcp/src/tools/workflowTools.ts`
- Test: `apps/workflow-mcp/test/server.test.ts`
- Create: `.vscode/mcp.example.json`
- Create: `.github/skills/start-ticket/SKILL.md`
- Create: `.github/skills/resume-workflow/SKILL.md`
- Create: `.github/skills/prepare-pr/SKILL.md`
- Create: `.github/agents/requirement-analyst.agent.md`
- Create: `.github/agents/solution-architect.agent.md`
- Create: `.github/agents/pr-reviewer.agent.md`
- Test: `packages/contracts/test/customizations.test.ts`

**Interfaces:**
- MCP tools: `workflow_list_my_tasks`, `workflow_get_task_context`, `workflow_claim_task`, `workflow_submit_artifact`, `workflow_request_approval`, `workflow_complete_task`.
- MCP imports no VS Code, MongoDB driver, artifact-backend SDK, model SDK, or transition-policy code.

- [ ] **Step 1: Write failing MCP protocol tests**

Test discovery, input validation, cancellation, correlation propagation, safe error mapping, and secret redaction against a fake HTTP service.

- [ ] **Step 2: Implement the thin stateless MCP gateway**

Diagnostics go to stderr; protocol responses go to stdout. Invalid configuration exits non-zero. Auth is a user-scoped provider, with demo header allowed only for localhost.

- [ ] **Step 3: Write customization validation tests**

Require valid frontmatter, lowercase skill names, human approval gates, no cloud/background assumptions, no Jenkins scanning, no direct persistence-backend access, and no company-specific values.

- [ ] **Step 4: Implement Skills and read-only Reviewer Agent**

`/start-ticket` fetches context, produces requirement analysis, requests approval, then stops. `/resume-workflow` inspects server state first. `/prepare-pr` reports tests/risks but never pushes or opens a PR automatically.

- [ ] **Step 5: Run and commit**

```powershell
pnpm --filter @sdlc/workflow-mcp test
pnpm --filter @sdlc/contracts test
git add apps/workflow-mcp .vscode .github packages/contracts
git commit -m "feat: add local mcp and copilot workflows"
```

---

### Task 5: Build the UI design system with UI/UX Pro Max

**Files:**
- Create: `docs/ui-ux/ui-ux-pro-max-review.md`
- Create: `packages/ui/package.json`
- Create: `packages/contracts/schemas/requirement-report-v1.schema.json`
- Create: `packages/contracts/schemas/test-report-v1.schema.json`
- Create: `packages/contracts/schemas/manual-e2e-case-v1.schema.json`
- Create: `packages/ui/src/tokens.css`
- Create: `packages/ui/src/TaskList.tsx`
- Create: `packages/ui/src/TaskStatusBadge.tsx`
- Create: `packages/ui/src/ApprovalPanel.tsx`
- Create: `packages/ui/src/ReportFrame.tsx`
- Create: `packages/ui/src/EmptyState.tsx`
- Create: `packages/ui/src/ErrorState.tsx`
- Create: `packages/ui/src/reports/RequirementReport.tsx`
- Create: `packages/ui/src/reports/TestReport.tsx`
- Create: `packages/ui/src/reports/ManualE2ERunner.tsx`
- Test: `packages/ui/test/accessibility.test.tsx`
- Test: `packages/ui/test/manualE2ERunner.test.tsx`

**Interfaces:**
- Theme-aware semantic React components, no `vscode-webview-ui-toolkit`.
- Status always has text, icon, accessible name, and optional color.
- Manual result is one of `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`.

- [ ] **Step 1: Invoke UI/UX Pro Max or record the fallback**

Read and use the installed `ui-ux-pro-max` Skill. Run its local, standard-library-only search script for the VSIX internal-workbench design system and targeted accessibility guidance. Persist only reviewed output under `docs/ui-ux`; do not install another CLI or send private project data to a remote service. If the Skill is unavailable, document the failure category and complete the equivalent information architecture, keyboard/focus, contrast, empty/loading/error/offline/stale, graph alternative, light/dark theme, and real-task usability checklist.

- [ ] **Step 2: Write failing accessibility and report tests**

Test keyboard order, visible focus, non-color states, long content, dark/light variables, permission/offline/stale states, explicit approval confirmation, and rejection of manual PASS without actor role, time, build fingerprint, actual result, and evidence or waiver.

- [ ] **Step 3: Implement minimal components and schemas**

Use `--vscode-*` CSS variables with browser-safe defaults. Render automated test evidence separately from unexecuted AI-generated manual cases. Sanitize all Markdown-derived HTML.

- [ ] **Step 4: Run and commit**

```powershell
pnpm --filter @sdlc/ui test
git add packages/ui packages/contracts docs/ui-ux
git commit -m "feat: add accessible html workflow reports"
```

---

### Task 6: Build the VSIX workbench without model dependencies

**Files:**
- Create: `apps/vscode-extension/package.json`
- Create: `apps/vscode-extension/src/extension.ts`
- Create: `apps/vscode-extension/src/api/workflowClient.ts`
- Create: `apps/vscode-extension/src/polling/taskPoller.ts`
- Create: `apps/vscode-extension/src/views/taskTreeProvider.ts`
- Create: `apps/vscode-extension/src/views/statusBar.ts`
- Create: `apps/vscode-extension/src/webview/reportPanel.ts`
- Create: `apps/vscode-extension/src/webview/approvalPanel.ts`
- Create: `apps/vscode-extension/src/diagnostics/mcpHealth.ts`
- Test: `apps/vscode-extension/test/taskPoller.test.ts`
- Test: `apps/vscode-extension/test/extension.test.ts`

**Interfaces:**
- Produces Activity Bar task view, status item, HTML reports, approval command, manual refresh, MCP diagnostics, and “Copy Copilot Command”.
- Consumes Workflow REST only; never queries MongoDB, Jira, GitHub, Jenkins, or an artifact backend directly.

- [ ] **Step 1: Write failing polling and lifecycle tests**

Cover immediate refresh, 60-second foreground poll, five-minute background poll, focus refresh, exponential backoff, ETag/cursor reuse, deactivate cancellation, and no polling while signed out.

- [ ] **Step 2: Implement REST client, poller, views, and safe Webviews**

Use `onStartupFinished`, strict CSP, nonces, sanitized report content, and current task version on approvals. Commands: `sdlc.refreshTasks`, `sdlc.openTask`, `sdlc.openReport`, `sdlc.approve`, `sdlc.copyCopilotCommand`, `sdlc.checkMcpHealth`.

- [ ] **Step 3: Add a static guard against model APIs**

The test scans extension sources and fails on `vscode.lm`, `selectChatModels`, `sendRequest`, model SDK dependencies, or Language Model Tool registration.

- [ ] **Step 4: Run tests, package, and commit**

```powershell
pnpm --filter @sdlc/vscode-extension test
pnpm --filter @sdlc/vscode-extension package
git add apps/vscode-extension
git commit -m "feat: add sdlc vscode workbench"
```

Expected: tests PASS and a `.vsix` under `apps/vscode-extension/dist/`.

---

### Task 7: Verify the complete public vertical slice and create the handoff manifest

**Files:**
- Create: `apps/web-ui/src/App.tsx`
- Create: `apps/web-ui/package.json`
- Create: `apps/web-ui/vite.config.ts`
- Create: `apps/web-ui/index.html`
- Create: `e2e/public-mvp.spec.ts`
- Create: `scripts/start-demo.ps1`
- Create: `scripts/stop-demo.ps1`
- Create: `docs/demo/public-mvp-walkthrough.md`
- Create: `docs/handoff/PUBLIC_DELIVERY_MANIFEST.md`
- Modify: `README.md`

**Interfaces:**
- Scenario: mock `DEMO-123` → task → MCP claim/result → requirement approval → mock CI → manual E2E → completed HTML report/audit history.

- [ ] **Step 1: Write the failing Playwright/API E2E**

Start local services; create `DEMO-123`; observe it in REST/Web UI; invoke MCP through a protocol client; upload a fictional report; approve it; attach mock CI and manual E2E outcomes; verify status and audit history.

- [ ] **Step 2: Run E2E and confirm failure**

Run: `pnpm e2e:public-mvp`
Expected: FAIL before final glue exists.

- [ ] **Step 3: Add only the glue required by the scenario**

Do not add enterprise adapters, graph scanning, Backstage, vector search, product mobile apps, or cloud deployment.

- [ ] **Step 4: Run the full verification suite**

```powershell
.\mvnw.cmd verify
pnpm install --frozen-lockfile
pnpm lint
pnpm test
pnpm e2e:public-mvp
pnpm --filter @sdlc/vscode-extension package
```

Expected: all tests PASS, VSIX produced, secret scan clean, and the public demo reproducible without a local database, object store, or container runtime.

- [ ] **Step 5: Complete the public delivery manifest**

Copy `docs/handoff/public-delivery-manifest-template.md` to `docs/handoff/PUBLIC_DELIVERY_MANIFEST.md` and fill every completed item, mock assumption, command, limitation, and internal-only work item. Do not claim internal compatibility.

- [ ] **Step 6: Commit the verified MVP**

```powershell
git add .
git commit -m "feat: complete public sdlc mvp vertical slice"
```

---

## Required Follow-up Plans

After this MVP, create and approve independent plans in this order:

1. `cross-repo-journey-onboarding`: Catalog, Repo/Journey onboarding, parent/sub-workflows, dependency DAG, account-opening pilot.
2. `knowledge-and-document-freshness`: Jira/Confluence/PR adapters, BM25/link graph, source manifests, stale-document tasks, and a separately approved LLM Wiki experiment. LLM Wiki must preserve immutable sources, provenance, access boundaries, and local-Copilot-only inference; it is not part of this MVP.
3. `product-repository-profiles`: Java Spring API, business Web, iOS, Android onboarding, Skills, test/report profiles, and API-contract linking.
4. `enterprise-adapters-and-security`: real GHES/Jira/Confluence authentication, RBAC, webhook hardening, company Mongo configuration/integration tests, and Jira attachment policy.
5. `team-scale-and-operations`: organization distribution, HA, backup/restore, retention, observability, capacity, and support bundles.

Every follow-up plan must separate public implementation from internal validation and require the non-code internal completion report.
