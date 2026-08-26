# Internal-shaped Simulation Increment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement production-shaped company integration boundaries, deterministic simulators, Journey onboarding, identity/Pod routing, Mongo mappings, VSIX/MCP visibility, and complete evidence reports without claiming real internal validation.

**Architecture:** Workflow Service owns typed ports, policies, versioned runtime state, adapter simulations, and evidence classification. Local MCP and VSIX expose bounded commands and read models but never call a model. Public tests use Fake Profile and deterministic transports; real company transports and connectivity remain internal configuration and validation.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Spring Data MongoDB, JUnit 5, TypeScript, MCP SDK 1.30.0, Zod, VS Code Extension API, React, Vitest, Playwright, pnpm 10.

## Global Constraints

- AI reasoning is available only through an interactive local VS Code GitHub Copilot Chat session initiated by a user.
- No Workflow Service, adapter, MCP, VSIX, test, build, Jenkins, or persistence component may call a model or use MCP sampling.
- No Docker, Compose, local MongoDB, embedded database, Testcontainers, MinIO, GridFS, or S3 dependency.
- Public fixtures use only fictional identities, `example.invalid`, `DEMO-123`, `EPIC-DEMO-1`, `REPO_A`, and fictional Journey/API names.
- Evidence uses only `SIMULATED_PASS`, `CONTRACT_PASS`, `INTERNAL_VALIDATION_REQUIRED`, or `BLOCKED`.
- Real company connectivity and production readiness remain `INTERNAL_VALIDATION_REQUIRED`.
- Every mutation requires authenticated actor attribution, correlation ID, expected version, and an audit event.
- Implementation follows test-first red/green/refactor; every task ends with an independently reviewable commit.

---

### Task 1: Evidence, identity, Pod, and Journey contracts

**Files:**
- Create: `packages/contracts/schemas/evidence-status-v1.schema.json`
- Create: `packages/contracts/schemas/enterprise-principal-v1.schema.json`
- Create: `packages/contracts/schemas/pod-roster-v1.schema.json`
- Create: `packages/contracts/schemas/integration-diagnostic-v1.schema.json`
- Create: `packages/contracts/schemas/journey-manifest-v1.schema.json`
- Modify: `packages/contracts/src/types.ts`
- Test: `packages/contracts/test/internal-shaped-schemas.test.ts`

**Interfaces:**
- Produces: `EvidenceStatus`, `EnterprisePrincipal`, `PodMembership`, `IntegrationDiagnostic`, `JourneyManifest`, and strict JSON Schema version `1.0`.
- Consumes: existing AJV strict-schema test pattern and fictional-data policy.

- [ ] **Step 1: Write failing contract tests**

Add tests that load all five schemas, validate complete fictional examples, and reject unknown evidence status, raw email, duplicate membership IDs, missing Journey provenance, and an HTTP edge without compatibility data.

```ts
expect(validateEvidence({ schemaVersion: "1.0", status: "REAL_PASS" })).toBe(false);
expect(validateJourney({ ...completeJourney, httpEdges: [{ ...edge, provenance: undefined }] })).toBe(false);
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `pnpm --filter @sdlc/contracts test -- internal-shaped-schemas.test.ts`

Expected: FAIL because schemas and exported types do not exist.

- [ ] **Step 3: Implement strict schemas and matching types**

Export exact unions and primary shapes:

```ts
export type EvidenceStatus = "SIMULATED_PASS" | "CONTRACT_PASS" | "INTERNAL_VALIDATION_REQUIRED" | "BLOCKED";
export interface EnterprisePrincipal { schemaVersion: "1.0"; principalId: string; employeeId: string; displayLabel: string; maskedEmail?: string; source: "GITHUB_ENTERPRISE" | "ADMIN_BINDING" | "SSO"; githubLogin?: string; }
export interface IntegrationDiagnostic { provider: "JIRA" | "CONFLUENCE" | "GHES" | "JENKINS" | "SPLUNK"; status: EvidenceStatus; observedAt: string; source: string; safeDetail: string; }
```

Define Journey repositories, screens, HTTP edges, release policy, feature flag, E2E owner, and provenance as bounded arrays with `additionalProperties: false`.

- [ ] **Step 4: Run focused and full contract tests**

Run: `pnpm --filter @sdlc/contracts test`

Expected: all contract test files pass.

- [ ] **Step 5: Commit**

```powershell
git add packages/contracts
git commit -m "feat: add internal-shaped workflow contracts"
```

### Task 2: Enterprise identity, Pod import, and deterministic assignment

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/EnterprisePrincipal.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/identity/IdentityBindingService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/evidence/EvidenceStatus.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/pod/PodMembership.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/pod/PodRoster.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/pod/PodRosterRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/pod/InMemoryPodRosterRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/pod/PodRosterService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/assignment/TaskAssignment.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/assignment/TaskAssignmentRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/assignment/InMemoryTaskAssignmentRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/assignment/AssignmentService.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/InternalReadinessController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/identity/IdentityBindingServiceTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/pod/PodRosterServiceTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/assignment/AssignmentServiceTest.java`

**Interfaces:**
- Produces: `IdentityBindingService.bindAdminPrincipal(employeeId, displayLabel, maskedEmail)`, `PodRosterService.importRoster(expectedRevision, rows, actorId, correlationId)`, and `AssignmentService.assign(ticketId, journeyId, requiredRole, explicitPrincipalId)`.
- Consumes: evidence vocabulary, `AuditEventRepository`, `Clock`, and optimistic expected revision.

- [ ] **Step 1: Write failing identity and routing tests**

Cover a Scrum Master with no GitHub login, masked email enforcement, duplicate active employee rejection, inactive-member exclusion, stale revision conflict, explicit assignee precedence, deterministic role match, and unassigned fallback.

```java
EnterprisePrincipal principal = service.bindAdminPrincipal("EMP-100", "Fictional SM", "f***@example.invalid");
assertNull(principal.githubLogin());
assertEquals(IdentitySource.ADMIN_BINDING, principal.source());
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `.\mvnw.cmd -pl apps/workflow-service -Dtest=IdentityBindingServiceTest,PodRosterServiceTest,AssignmentServiceTest test`

Expected: FAIL because the identity, Pod, and assignment packages do not exist.

- [ ] **Step 3: Implement immutable domain records and in-memory repositories**

Use validation constructors, copy-on-write roster revisions, sorted principal IDs for deterministic selection, and an `UNASSIGNED` result when no active role matches. Store no raw email.

- [ ] **Step 4: Add read endpoints and Fake Profile beans**

Expose:

```text
GET  /api/v1/internal-readiness/identity
GET  /api/v1/internal-readiness/pods/{journeyId}
GET  /api/v1/internal-readiness/assignments/{ticketId}
POST /api/v1/internal-readiness/pods/import
```

The import endpoint requires `expectedRevision` and `X-Correlation-ID`; actor identity is derived from the authenticated `CurrentUser`, never an editable actor header. It returns row errors without partial persistence.

- [ ] **Step 5: Run focused and full Java tests**

Run: `.\mvnw.cmd -pl apps/workflow-service test`

Expected: all unit tests pass.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service/src/main apps/workflow-service/src/test
git commit -m "feat: add enterprise identity and pod routing"
```

### Task 3: Mongo-shaped documents and repository adapters

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/WorkflowTaskDocument.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/AuditEventDocument.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/ArtifactDocument.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/WebhookDeliveryDocument.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/PodRosterDocument.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/TaskAssignmentDocument.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/MongoWorkflowTaskRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/MongoAuditEventRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/MongoWebhookDeliveryRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/MongoPodRosterRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/persistence/MongoTaskAssignmentRepository.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/MongoRuntimeConfiguration.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/artifact/MongoDocumentArtifactStore.java`
- Modify: `apps/workflow-service/src/main/resources/application-mongodb.example.yml`
- Create: `apps/workflow-service/src/main/resources/mongo-indexes-v1.json`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/persistence/MongoDocumentMappingTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/persistence/RepositoryContractFixtureTest.java`

**Interfaces:**
- Produces: Spring Data documents with explicit collection names, mapper methods `fromDomain`/`toDomain`, and adapters implementing existing repository ports.
- Consumes: existing `WorkflowTaskRepository`, `AuditEventRepository`, `ArtifactStore`, `WebhookDeliveryRepository`, `PodRosterRepository`, and `TaskAssignmentRepository`.

- [ ] **Step 1: Write failing round-trip and index tests**

Assert lossless task/version/lease mapping, immutable audit IDs, artifact hash retention, webhook unique delivery ID, Pod revision, assignment revision, and all six collection/index declarations.

```java
WorkflowTask roundTrip = WorkflowTaskDocument.fromDomain(task).toDomain();
assertEquals(task, roundTrip);
```

- [ ] **Step 2: Run focused mapping tests and verify RED**

Run: `.\mvnw.cmd -pl apps/workflow-service -Dtest=MongoDocumentMappingTest,RepositoryContractFixtureTest test`

Expected: FAIL because document mappers and adapters do not exist.

- [ ] **Step 3: Implement document mappers and adapters**

Use `MongoOperations`; enforce optimistic task save with `_id + version` query, append-only audit insert, unique webhook delivery insert, and revision compare-and-set for Pod/assignment. Translate duplicate-key/version misses to existing domain conflicts without exposing driver messages.

- [ ] **Step 4: Add the non-Fake Mongo configuration**

Activate `MongoRuntimeConfiguration` only under profile `mongo`. It wires all six adapters and refuses startup when placeholders are unresolved. Do not add connectivity tests or local database startup.

- [ ] **Step 5: Run mapping, configuration, and all Java unit tests**

Run: `.\mvnw.cmd -pl apps/workflow-service test`

Expected: all unit tests pass without a Mongo process.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service
git commit -m "feat: add mongo-shaped workflow repositories"
```

### Task 4: Enterprise transport and Jira/Confluence/GHES/Jenkins/Splunk adapters

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseProvider.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseRequest.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseResponse.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseTransport.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseCancellation.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/DeterministicFakeTransport.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseAdapterProperties.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseCredentialProvider.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/JavaHttpEnterpriseTransport.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/enterprise/EnterpriseAdapterException.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/JiraEnterpriseAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/ConfluenceEnterpriseAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/GhesEnterpriseAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/JenkinsEnterpriseAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/SplunkDiagnosticAdapter.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/integration/IntegrationDiagnosticService.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/api/InternalReadinessController.java`
- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/config/FakeRuntimeConfiguration.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/enterprise/DeterministicFakeTransportTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/integration/EnterpriseAdaptersTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/integration/SplunkDiagnosticAdapterTest.java`

**Interfaces:**
- Produces: `EnterpriseTransport.execute(EnterpriseRequest, Duration, EnterpriseCancellation)` and `IntegrationDiagnosticService.diagnostics()`.
- Consumes: fictional provider fixtures, bounded JSON envelopes, correlation/idempotency metadata, and `StructuredLogSanitizer`.

- [ ] **Step 1: Write failing provider contract tests**

Cover success, pagination, timeout, 429 retry classification, 401 no-retry, malformed JSON, stable Jira comment idempotency key, unavailable attachment, stale/unknown Jenkins state, Confluence untrusted provenance, GHES check normalization, and Splunk field allowlist/32 KiB batch limit.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `.\mvnw.cmd -pl apps/workflow-service -Dtest=DeterministicFakeTransportTest,EnterpriseAdaptersTest,SplunkDiagnosticAdapterTest test`

Expected: FAIL because transport and enterprise adapters do not exist.

- [ ] **Step 3: Implement bounded transport records and scripted fake ledger**

`EnterpriseRequest` contains provider, operation, method, provider-relative path, bounded headers/body, correlation ID, idempotency key, and cursor. The fake transport resolves it against `example.invalid`, records sanitized request metadata, and returns a queued response or safe categorized failure. `JavaHttpEnterpriseTransport` resolves only against allowlisted configured HTTPS base URIs, obtains authorization through `EnterpriseCredentialProvider`, enforces timeout/body limits, and is tested with an injected HTTP executor so tests make no network call.

- [ ] **Step 4: Implement provider adapters and diagnostics**

Map provider payloads into existing ticket/SCM/CI ports plus read-only metadata records. Return evidence status `SIMULATED_PASS` for scripted behavior and `INTERNAL_VALIDATION_REQUIRED` for real connectivity.

- [ ] **Step 5: Run focused and full Java tests**

Run: `.\mvnw.cmd -pl apps/workflow-service test`

Expected: all tests pass with no network call.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service
git commit -m "feat: add deterministic enterprise adapters"
```

### Task 5: Journey onboarding, gap analysis, and HTML report

**Files:**
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyManifest.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyRepositoryEntry.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyHttpEdge.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyGap.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyGapAnalyzer.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyReportRenderer.java`
- Create: `apps/workflow-service/src/main/java/dev/sdlc/workflow/journey/JourneyController.java`
- Create: `fixtures/journeys/account-opening-fictional-v1.json`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/journey/JourneyGapAnalyzerTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/journey/JourneyReportRendererTest.java`
- Test: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JourneyControllerIT.java`

**Interfaces:**
- Produces: `JourneyGapAnalyzer.analyze(JourneyManifest): JourneyAnalysis` and `JourneyReportRenderer.render(JourneyAnalysis): String`.
- Consumes: Journey schema, evidence status, common-header/release/feature-flag policy, and safe HTML escaping.

- [ ] **Step 1: Write failing gap and rendering tests**

Cover a complete fictional Account Opening manifest plus missing API/Web/iOS/Android repository, missing payload schema, missing header evidence, breaking change, absent native window, absent feature flag, missing E2E owner, and absent provenance. Assert HTML escapes injected labels and includes text/icon evidence status.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `.\mvnw.cmd -pl apps/workflow-service -Dtest=JourneyGapAnalyzerTest,JourneyReportRendererTest,JourneyControllerIT test`

Expected: FAIL because Journey domain and endpoints do not exist.

- [ ] **Step 3: Implement immutable Journey model and deterministic analyzer**

Return ordered gap codes and coverage counts. A structurally complete fictional manifest returns `CONTRACT_PASS`; all real repository relationships remain `INTERNAL_VALIDATION_REQUIRED` until local evidence refs are supplied.

- [ ] **Step 4: Implement report and endpoints**

Expose:

```text
POST /api/v1/journeys/validate
POST /api/v1/journeys/analyze
POST /api/v1/journeys/report
```

Limit manifest size and render a standalone safe HTML report.

- [ ] **Step 5: Run focused and full Java tests**

Run: `.\mvnw.cmd -pl apps/workflow-service verify`

Expected: all unit and integration tests pass.

- [ ] **Step 6: Commit**

```powershell
git add apps/workflow-service fixtures
git commit -m "feat: add journey onboarding gap analysis"
```

### Task 6: Local MCP internal-readiness tools

**Files:**
- Modify: `apps/workflow-mcp/src/client.ts`
- Modify: `apps/workflow-mcp/src/tools/workflowTools.ts`
- Test: `apps/workflow-mcp/test/server.test.ts`
- Create: `apps/workflow-mcp/test/internalReadinessTools.test.ts`

**Interfaces:**
- Produces tools `workflow_get_identity`, `workflow_validate_pod_roster`, `workflow_import_pod_roster`, `workflow_get_integration_diagnostics`, `workflow_analyze_journey`, and `workflow_get_next_internal_validation`.
- Consumes the new Workflow Service endpoints, expected version, cancellation signal, and safe error wrapper.

- [ ] **Step 1: Write failing MCP discovery and behavior tests**

Assert all six tools are discoverable, read-only annotations are accurate, import requires `confirmed: true` and `expectedRevision`, oversized Journey input is rejected, cancellation propagates, and errors contain correlation IDs but no provider body.

- [ ] **Step 2: Run focused MCP tests and verify RED**

Run: `pnpm --filter @sdlc/workflow-mcp test -- internalReadinessTools.test.ts`

Expected: FAIL because tools and client methods do not exist.

- [ ] **Step 3: Implement typed client calls and bounded Zod inputs**

Keep the existing `safe()` wrapper. Mutation tool description must state that it persists only after explicit confirmation; read tools return evidence status and observation time.

- [ ] **Step 4: Run MCP tests and build**

Run: `pnpm --filter @sdlc/workflow-mcp test; pnpm --filter @sdlc/workflow-mcp build`

Expected: tests and TypeScript build pass.

- [ ] **Step 5: Commit**

```powershell
git add apps/workflow-mcp
git commit -m "feat: expose internal readiness mcp tools"
```

### Task 7: VSIX evidence, identity, integration, and Journey views

**Files:**
- Modify: `apps/vscode-extension/package.json`
- Modify: `apps/vscode-extension/src/api/workflowClient.ts`
- Create: `apps/vscode-extension/src/views/readinessTreeProvider.ts`
- Create: `apps/vscode-extension/src/webview/journeyReportPanel.ts`
- Modify: `apps/vscode-extension/src/extension.ts`
- Test: `apps/vscode-extension/test/workflowClient.test.ts`
- Create: `apps/vscode-extension/test/readinessTreeProvider.test.ts`
- Modify: `apps/vscode-extension/test/extension.test.ts`

**Interfaces:**
- Produces commands `sdlc.refreshReadiness`, `sdlc.openJourneyReport`, and read models for identity, Pod assignment, provider diagnostics, and Journey gaps.
- Consumes Workflow Service readiness endpoints, evidence/freshness types, and safe webview HTML utilities.

- [ ] **Step 1: Write failing client and view-model tests**

Assert evidence labels always include text and icon, observation time/source appear, stale/offline/empty states are distinct, non-GitHub Scrum Master identity renders, and the static no-model guard still rejects `vscode.lm`, `selectChatModels`, `sendRequest`, and `LanguageModelTool`.

- [ ] **Step 2: Run focused VSIX tests and verify RED**

Run: `pnpm --filter sdlc-workbench test -- readinessTreeProvider.test.ts`

Expected: FAIL because readiness API and provider do not exist.

- [ ] **Step 3: Implement client methods and readiness provider**

Reuse existing nine top-level views: show identity/Pod/integration/Journey sections inside Scrum Master, Developer, MCP Center, and Diagnostics rather than adding more activity-bar views. Use `ThemeIcon` plus evidence text and ISO observed time.

- [ ] **Step 4: Implement safe Journey HTML panel and commands**

Webview scripts remain disabled for reports. Errors go to Output Channel with sanitized correlation metadata.

- [ ] **Step 5: Run tests, typecheck, build, and package**

Run: `pnpm --filter sdlc-workbench test; pnpm --filter sdlc-workbench typecheck; pnpm --filter sdlc-workbench package`

Expected: all tests pass and `dist/sdlc-workbench.vsix` is generated.

- [ ] **Step 6: Commit**

```powershell
git add apps/vscode-extension
git commit -m "feat: add internal readiness vscode views"
```

### Task 8: Full simulated E2E, design HTML, and complete reports

**Files:**
- Modify: `apps/web-ui/src/App.tsx`
- Modify: `apps/web-ui/src/app.css`
- Modify: `apps/web-ui/test/App.test.tsx`
- Create: `e2e/internal-shaped-simulation.spec.ts`
- Modify: `playwright.config.ts`
- Modify: `docs/superpowers/specs/2026-08-15-local-copilot-sdlc-platform-v2-design.md`
- Modify: `docs/superpowers/specs/2026-08-15-local-copilot-sdlc-platform-v2-design.html`
- Create: `docs/implementation/internal-shaped-implementation-inventory.md`
- Create: `docs/reference/internal-shaped-contract-reference.md`
- Create: `docs/reports/simulated-enterprise-integration-report.md`
- Create: `docs/handoff/INTERNAL_CONNECTION_GUIDE.md`
- Modify: `docs/handoff/PUBLIC_DELIVERY_MANIFEST.md`
- Modify: `docs/handoff/INTERNAL_AGENT_HANDOFF.md`
- Modify: `docs/handoff/internal-agent-completion-report-template.md`
- Modify: `docs/verification/public-verification-2026-08-16.md`
- Modify: `README.md`

**Interfaces:**
- Produces a fictional end-to-end flow, a human-readable HTML design, an exact implementation inventory, and narrowed internal-only validation list.
- Consumes all prior tasks, UI/UX Pro Max accessibility rules, and evidence vocabulary.

- [ ] **Step 1: Write failing Web component and Playwright tests**

The browser scenario creates `EPIC-DEMO-1`/`DEMO-123`, binds a fictional non-GitHub Scrum Master, imports a fictional Pod, assigns the ticket, displays five simulated adapter diagnostics, analyzes the fictional Account Opening Journey, verifies gap/evidence labels, and opens a safe HTML report.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `pnpm --filter @sdlc/web-ui test; pnpm exec playwright test e2e/internal-shaped-simulation.spec.ts`

Expected: FAIL because readiness UI and endpoints are absent.

- [ ] **Step 3: Implement accessible readiness UI**

Use existing product visual language, semantic headings/tables, keyboard access, visible focus, 44 px touch targets on narrow screens, text plus icon status, and explicit simulated/internal-validation copy. Do not add charts when a table communicates the exact mapping more clearly.

- [ ] **Step 4: Update design and evidence documents**

Record every component, source file group, endpoint, MCP tool, schema, configuration key, simulated scenario, exact test command/count, limitation, and internal validation action. The HTML design must contain the architecture/data-flow diagrams and the same evidence classification as Markdown.

- [ ] **Step 5: Run complete verification**

Run all commands fresh:

```powershell
pnpm install --frozen-lockfile
.\mvnw.cmd verify
pnpm lint
pnpm test
pnpm build
pnpm e2e:public-mvp
pnpm exec playwright test e2e/internal-shaped-simulation.spec.ts
.\scripts\tests\stop-demo.test.ps1
pnpm --filter sdlc-workbench package
pnpm audit --audit-level low
git diff --check
```

Also start the Fake-profile demo, verify Workflow health `UP` and Web HTTP 200, stop it, and confirm ports 8080/4173 are released. Run secret, prohibited-infrastructure, model-client, real-domain, incomplete-marker, and evidence-vocabulary scans.

- [ ] **Step 6: Review requirements line by line**

Map all ten acceptance criteria in `2026-08-16-internal-shaped-simulation-design.md` to code/tests/reports. Any criterion lacking evidence remains incomplete and prevents the completion claim.

- [ ] **Step 7: Commit and publish**

```powershell
git add README.md apps packages e2e fixtures docs policies manifests mcp playwright.config.ts pnpm-lock.yaml
git commit -m "feat: complete internal-shaped simulation increment"
git push origin agent/mvp-vertical-slice
```

Confirm Draft PR #1 head SHA equals local `HEAD` and update the PR body with final counts and remaining internal-only validation.
