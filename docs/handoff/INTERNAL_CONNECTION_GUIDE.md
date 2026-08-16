# Internal connection guide

Use this only in the company fork. Do not copy internal values, code, screenshots or raw logs back to the public repository.

## 1. Baseline and evidence

Record the public commit and run the complete public verification unchanged. Treat public fake outcomes as `SIMULATED_PASS`. Create internal evidence IDs before marking any connector PASS.

## 2. Managed MongoDB

Activate the `mongo` profile and supply environment-backed values described by `apps/workflow-service/src/main/resources/application-mongodb.example.yml`. Use the reviewed `mongo-indexes-v1.json`; do not start a local database or container. Verify TLS, least-privilege credentials, optimistic writes, restart/resume, backup/restore, retention and capacity. GridFS and S3 are not MVP dependencies. Keep structured report content in Mongo; project only a concise status/summary to Jira if attachments are unavailable.

## 3. Identity

MVP may use GitHub Enterprise login plus an administrator-approved employee binding. Import can provision `principalId` before a user installs VSIX, including Scrum Masters without GitHub access. Keep authorization audit-only until SSO and role ownership are approved. Never store the personnel roster in central Git.

## 4. Enterprise adapters

For each provider, configure base URI, secret reference, timeout, response size and TLS/proxy through the internal configuration system. Implement `EnterpriseCredentialProvider`; do not put tokens in VSIX, MCP configuration, Git, Jira comments or logs. Run authentication, authorization, rate-limit, timeout, cancellation, malformed payload, redaction and retry tests.

Jenkins remains a deterministic CI/status system. Existing Groovy pipelines may call scanners and publish checks, but they do not invoke an Agent or model. Copilot analysis remains an interactive local action.

## 5. VSIX and Local MCP

Build and install the VSIX, set the Workflow Service URL, and install reviewed central customizations. Configure Local MCP from the MCP Center guide. Validate foreground/background polling, manual refresh, focus refresh, offline/stale status, log redaction and no Language Model API usage. A non-GitHub Scrum Master needs an approved alternate service authentication path if GHES login cannot be used.

## 6. Pilot Journey

Start with a complete source list when available: API, Web, iOS, Android repositories plus approved Jira/Confluence/Figma material. Partial onboarding is allowed but becomes a named gap. At pinned commits, extract page/screen-to-API method/path, request/response schema, common header, auth class and provenance. For Account Opening verify WebView/native boundaries, Web/API-first and Native-later compatibility, app-version/header behavior, AWS toggle, rollback and QA E2E ownership. Ask the user for these rules for other Journeys.

## 7. Return report

Complete `internal-agent-completion-report-template.md`. Return only that report, with abstract deviations, counts and internal evidence IDs. Do not include code, diffs, internal names/URLs/IPs, raw errors, real tickets/APIs, logs, screenshots or configuration values. Public review will classify contract changes and advise the internal Agent without receiving company artifacts.
