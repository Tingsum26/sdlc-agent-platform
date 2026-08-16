# Internal-shaped simulation increment design

**Date:** 2026-08-16  
**Status:** Approved concept; written-spec review pending  
**Parent design:** `2026-08-15-local-copilot-sdlc-platform-v2-design.md`

## 1. Purpose

Reduce the amount and risk of work delegated to an internal Agent by implementing every company-facing capability that can be proved without company connectivity. The increment uses production-shaped ports, configuration, persistence mappings, deterministic simulators, contract tests, and human-readable reports. It never represents simulated evidence as proof that a real company system works.

## 2. Evidence vocabulary

Every integration, report, and UI status uses exactly one of:

- `SIMULATED_PASS`: deterministic fictional provider behavior passed;
- `CONTRACT_PASS`: local schema, mapping, policy, or protocol contract passed;
- `INTERNAL_VALIDATION_REQUIRED`: code is ready for a real endpoint or environment, but no internal evidence exists;
- `BLOCKED`: a required prerequisite or contract is absent or incompatible.

`COMPLETE` remains valid only for public implementation scope. It cannot imply company deployment, connectivity, security approval, production readiness, or real Journey coverage.

## 3. Architectural approach

The approved approach is production-shaped adapters plus deterministic simulators and contract tests. Interface-only stubs are insufficient because they do not prove retry, mapping, idempotency, recovery, or failure behavior. A full fake enterprise platform is rejected because it would be expensive and could encode incorrect assumptions about company products.

All company-facing capabilities use the same boundary:

```text
Workflow domain / VSIX / Local MCP
                |
          typed application port
                |
     production-shaped adapter core
                |
       EnterpriseTransport interface
          /                 \
DeterministicFakeTransport  Internal HTTP transport
    public tests             internal configuration
```

No transport or service calls a model. Semantic reasoning remains an explicit local Copilot Chat action initiated by the user.

## 4. Delivery slices

### 4.1 Enterprise identity and Pod routing

Implement an immutable `EnterprisePrincipal` with principal ID, employee ID, display label, email hash or masked email, identity source, and optional GitHub login. A Scrum Master without GitHub access receives an administrator-bound employee principal; GitHub onboarding is not a prerequisite for receiving or viewing an assignment.

MVP authorization remains `AUDIT_ONLY`: authentication and actor attribution are required, while role data affects routing and display but does not deny actions. Every decision records principal ID, actor label, source, timestamp, and correlation ID.

Pod membership supports effective dates, active/inactive status, aliases, role, Journey scope, and source revision. Import is all-or-nothing, rejects duplicate active employee IDs, and produces a row-level validation report before confirmation. Assignment is deterministic: explicit ticket assignee, then matching active Pod role, then an unassigned queue. Teambook remains a future provider behind the same roster port.

### 4.2 Mongo persistence mapping

Provide Spring Data Mongo document models and adapters for:

- workflow tasks and optimistic version;
- immutable audit events;
- versioned structured artifacts and content hash;
- webhook delivery deduplication;
- Pod membership revisions;
- task assignments.

No local MongoDB, embedded MongoDB, Testcontainers, Docker, GridFS, S3, or MinIO is introduced. Public tests prove conversion, collection/index contracts, optimistic version semantics, immutable append behavior, and snapshot/reload behavior through deterministic repository contract fixtures. Only a company non-production MongoDB can prove driver connectivity, TLS, credentials, index creation privileges, backup/restore, retention, performance, and topology behavior.

Large binary evidence remains reference-only. Structured reports stay bounded Mongo documents; Jira receives a concise summary and an optional attachment only when internal policy permits it.

### 4.3 Enterprise adapters

Define a shared `EnterpriseTransport` contract with bounded request/response sizes, timeout, correlation ID, idempotency key, pagination cursor, safe error category, and cancellation. Adapter credentials never enter domain objects, logs, reports, or MCP results.

Provider capabilities are:

| Provider | Read | Write | Public proof |
|---|---|---|---|
| Jira | ticket/Epic fields, links, comments metadata | idempotent milestone comment and optional report attachment request | request mapping, pagination, retry/error classification, idempotency |
| Confluence | approved page metadata/content envelope | none in this increment | ACL/provenance envelope, pagination, injection/untrusted-source marking |
| GHES | repository/PR/check metadata | none | pagination, check normalization, rate-limit/error mapping |
| Jenkins | build/check summary and evidence link | none | state normalization, stale-build detection, unknown-state fallback |
| Splunk | none | bounded redacted diagnostic event batch | allowlisted fields, redaction, payload limit, retry classification |

The deterministic fake transport uses `example.invalid`, fictional identifiers, scripted responses, and a captured request ledger. It can simulate success, timeout, rate limit, duplicate delivery, unavailable attachment, malformed response, pagination, and partial provider failure.

### 4.4 Journey onboarding

Create a versioned Journey manifest containing:

- Journey and Domain identifiers;
- repository inventory with API, Web, iOS, Android, or supporting role;
- screen/page identifier and owning client technology;
- caller-to-API HTTP edge;
- method, normalized path, request/response schema reference, common-header rule, and authentication class;
- Web/API release readiness, Native Release Train, compatibility window, feature flag owner/provider, and rollback rule;
- automated and manual E2E ownership;
- provenance and last-verified commit/ref for every discovered edge.

The Account Opening fixture is fictional and contains no company repository, API, screen, or payload name. Coverage calculation reports missing repository classes, unproven HTTP edges, missing payload schemas, missing header evidence, breaking-change risks, missing native compatibility, absent flags, and missing E2E owners. A Journey cannot become `CONTRACT_PASS` while a required field is missing; it remains `INTERNAL_VALIDATION_REQUIRED` until real repository evidence is supplied locally.

### 4.5 VSIX and Local MCP

VSIX adds read-focused views or sections for identity binding status, Pod assignment, integration diagnostics, Journey coverage, and evidence classification. It polls Workflow Service and renders text plus icons; it does not infer provider health from stale cached data. Every row includes observed time and source.

Local MCP adds bounded, non-model tools for:

- validating/importing a Pod roster after explicit confirmation;
- reading identity and assignment state;
- reading integration diagnostic summaries;
- validating a Journey manifest and generating a gap report;
- retrieving the next internal-validation action.

Mutation tools require expected resource version and explicit user confirmation. MCP stdout remains protocol-only; diagnostics go to stderr.

## 5. State and data flow

```text
Local Copilot Agent
  -> Local MCP typed call
  -> Workflow Service authenticated command/query
  -> domain policy + optimistic version
  -> repository or enterprise adapter port
  -> deterministic fake transport (public) / approved HTTP transport (internal)
  -> audit event + evidence classification
  -> Mongo-shaped state
  -> VSIX polling / HTML report / concise Jira projection
```

Workflow Service is authoritative for task state, evidence classification, assignments, and reports. Jira is a human-visible projection. Git stores reviewed schemas, policies, customizations, templates, and Journey manifest source; Mongo stores runtime revisions and assignments.

## 6. Error and safety behavior

- Unknown provider payloads fail closed as `PROVIDER_CONTRACT_MISMATCH`.
- Timeouts and rate limits are retryable only for idempotent reads or writes carrying a stable idempotency key.
- Authentication, authorization, certificate, and policy failures are never automatically retried.
- Provider partial failure does not roll back authoritative workflow state; it records a pending projection or validation action.
- Logs include provider type, operation, status class, duration, correlation ID, and safe error category only.
- Confluence/Jira content is untrusted input and retains source/provenance markers for Copilot; it cannot override central instructions.
- Journey data never claims an HTTP relationship without a source manifest and evidence reference.
- Fake transport is disabled unless the `fake` profile is explicitly active.

## 7. Testing strategy

Implementation follows test-first red/green/refactor cycles.

1. Identity and Pod unit tests cover non-GitHub Scrum Master identity, duplicate rows, inactive membership, revision conflict, deterministic assignment, and audit attribution.
2. Mongo adapter contract tests cover domain/document round trips, optimistic version conflict, immutable audit append, webhook deduplication, artifact hash, and snapshot/reload semantics without a database process.
3. Enterprise adapter tests cover request mapping, pagination, timeout, rate limit, authentication failure, malformed payload, idempotent comment, unavailable attachment, stale Jenkins build, and Splunk redaction/size limits.
4. Journey tests cover complete fictional manifest, every gap category, compatibility/feature-flag policy, provenance, and HTML escaping.
5. MCP protocol tests cover discovery, validation, confirmation, cancellation, safe errors, and output bounds.
6. VSIX tests cover freshness, evidence labels, empty/error/offline states, keyboard access, and no Language Model API usage.
7. Browser E2E covers fictional Epic/Ticket → Pod assignment → Journey gap analysis → adapter diagnostics → report/approval/audit.
8. Static checks reject real domains, credentials, Docker/local database dependencies, model clients, unclassified evidence, and incomplete markers.

## 8. Documentation and reports

Update all of the following:

- parent design Markdown and human-readable HTML;
- public delivery manifest with implemented versus internally validated columns;
- implementation inventory by component/file/contract;
- API, MCP, schema, configuration, and index reference;
- simulated integration report with scenario IDs and evidence classes;
- complete verification report with exact test counts and commands;
- internal connection guide containing only variable names and abstract endpoint requirements;
- narrowed internal Agent handoff and redacted completion-report template;
- extension backlog retaining LLM Wiki, real code graph extraction, Teambook, SSO enforcement, GridFS/S3, and team-scale rollout.

## 9. Acceptance criteria

1. Every listed provider has a typed adapter, deterministic simulator, and failure-contract tests.
2. Identity and assignment work for a fictional Scrum Master without GitHub access.
3. Pod import is validated, versioned, auditable, and atomic.
4. All six Mongo aggregate types have production-shaped document mappings and index declarations without a local database dependency.
5. The fictional Account Opening manifest produces both a passing contract result and deterministic gap variants.
6. VSIX and MCP expose evidence classification and freshness without model calls.
7. Reports never label simulated behavior as real internal success.
8. Existing public MVP tests remain green and the new end-to-end scenario passes.
9. npm audit reports no known vulnerabilities and secret/prohibited-dependency scans pass.
10. The Draft PR contains updated design, implementation, verification, and internal handoff reports.

## 10. Explicitly unprovable outside the company

Real endpoint reachability; corporate TLS/proxy; SSO protocol and authorization; GHES/Jira/Confluence/Jenkins/Splunk versions and permissions; Copilot enterprise policy and model availability; Mongo connectivity/topology/backup/restore/performance; real repository and Journey relationships; Jira attachment policy; native release/toggle behavior; security, accessibility, license, and production approvals.

These items remain `INTERNAL_VALIDATION_REQUIRED` even when their public simulator and contract tests pass.
