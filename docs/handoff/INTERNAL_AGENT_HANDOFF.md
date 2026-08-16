# Internal Agent handoff

The public branch contains a verified Fake-profile vertical slice plus internal-shaped identity, Pod, Mongo, enterprise-adapter and Journey implementations. The internal Agent must connect and validate them without uploading company code, configuration, screenshots, URLs, logs, or data to the public repository.

## Required internal work

1. Verify VS Code/Copilot versions, Agent Skills, Custom Agents, model availability, MCP policy, hooks policy, and the reviewer-model names. Replace unsupported frontmatter only inside the internal fork.
2. Connect the provided identity binding to GitHub Enterprise login/administrator import, including the no-GitHub-account Scrum Master path. Keep MVP authorization `AUDIT_ONLY` until SSO/role design is approved.
3. Configure and validate the provided Mongo repositories for tasks, audits, artifacts, webhook deliveries, Pod membership, and assignments using `application-mongodb.example.yml` and reviewed indices. No GridFS dependency; if large attachment storage is unavailable, keep structured reports in Mongo and publish only a concise Jira summary/status.
4. Connect the provided transport/adapters to Jira, Confluence, GitHub Enterprise, Jenkins status and Splunk; add optional Figma/code-graph adapters. They contain no model. Validate HMAC, replay/deduplication, API limits, proxies, certificates and delegated identity.
5. Package and distribute the versioned customization bundle through the VSIX/approved GHES release path. Validate manifest, hash/signature, compatibility, last-known-good rollback, and VS Code discovery.
6. Import Pod rosters with the central `importing-pod-members` Skill. Validate aliases, effective dates, duplicates, inactive users, and Teambook future mapping without making onboarding dependent on every person installing the VSIX.
7. Replace `fixtures/journeys/account-opening-fictional-v1.json` with an internally generated, reviewed pilot manifest. For Account Opening only, verify complete API/Web/iOS/Android coverage, hybrid page-to-API calls, common header, request/response payloads, web/API-first and native-later release train, AWS toggles, compatibility and E2E ownership. Ask for release policy for every other Journey.
8. Validate Java Spring Boot/WebFlux, Web, iOS, and Android repository profiles, automated test generation, accessibility/tagging, manual E2E, CI/Jenkins, Jira milestone comments, Splunk logging, backup/restore, retention, capacity, and security review.

## Mandatory return artifact

Follow `INTERNAL_CONNECTION_GUIDE.md`, then return only a completed copy of `internal-agent-completion-report-template.md`. It must contain no code, diff, internal URL/IP, token, repository name, raw log, screenshot, real ticket/API/customer data, or configuration. Every PASS needs an internal evidence ID; list deviations and questions for public review in abstract terms.
