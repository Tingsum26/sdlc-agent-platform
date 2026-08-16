# Public SDLC MVP Pre-implementation Review

Date: 2026-08-16
Decision: **PROCEED** — no unresolved public-code blocker.

## Reviewed scope

- v2 platform design and HTML report;
- Public SDLC MVP implementation plan;
- public-to-internal handoff and completion-report templates;
- existing `importing-pod-members` Skill and tests;
- local toolchain and GitHub publishing prerequisites;
- model, persistence, security, logging, UI, testing, and deployment boundaries.

## Locked decisions

1. The implementation is the seven-task public MVP vertical slice, not every post-MVP enterprise adapter described in the long-term design.
2. All model inference remains user-initiated in local VS Code GitHub Copilot. Workflow Service, MCP, VSIX, Web UI, MongoDB, CI, and mocks contain no model client and do not use `vscode.lm`.
3. Public execution uses Fake repositories and fictional adapters. Company MongoDB is represented only by example YML, index manifests, ports, health diagnostics, and an internal validation contract.
4. No Docker, Compose, local MongoDB launcher, Embedded Mongo, Testcontainers, MinIO, S3 SDK, cloud Agent, background Agent, or Jenkins extension is introduced.
5. LLM Wiki, vector search, embeddings, Graph Scanner, cross-repository onboarding, and enterprise adapters are post-MVP plans.
6. Canonical structured artifacts are versioned. HTML is rendered on demand. Jira publication is a non-authoritative optional projection.
7. The VSIX is a REST/UI client. It does not directly access MongoDB, Jira, GitHub, Jenkins, MCP persistence, or model APIs.
8. The standalone Workflow MCP is a thin stdio gateway. It has no transition policy, persistence driver, or model dependency.
9. UI implementation uses the installed `ui-ux-pro-max` Skill, VS Code theme variables, strict CSP, keyboard support, non-color state semantics, light/dark themes, and explicit stale/offline/error states.
10. Public GitHub publication targets `Tingsum26/sdlc-agent-platform`. Initial visibility is private to avoid accidental exposure; visibility can be changed separately after review.

## Version baseline

- Java 17 and Spring Boot 3.5.16; this line supports Java 17 and Maven 3.6.3+.
- Maven Wrapper locks the public build independently of the workstation Maven installation.
- Node.js 20+ runtime contract, pnpm 10 lockfile, TypeScript 5.9, React 19, and MCP TypeScript SDK v1.x.
- MCP v2 is not used while its upstream line remains pre-stable for production.
- VSIX packaging uses a pinned `@vscode/vsce` development dependency and emits an installable `.vsix` artifact.

## Threat and privacy review

- No real company URLs, code, repository names, users, payloads, tickets, headers, or credentials are accepted into public fixtures.
- Webhook verification uses the raw request bytes, constant-time HMAC comparison, delivery deduplication, and explicit supported-event filtering.
- Logs and problem responses contain correlation IDs but omit request bodies, prompts, tokens, cookies, authorization headers, and raw roster data.
- Webviews use a strict Content Security Policy, per-render nonce, sanitized content, and explicit user confirmation for approval.
- Audit events attribute human and Agent/configuration versions without storing full chat transcripts.

## Delivery and recovery review

- Runtime progress is persisted by the Workflow Service contract; closing VS Code does not make an unsubmitted local result authoritative.
- Polling uses foreground/background intervals, focus refresh, ETag/cursor reuse, cancellation, and backoff.
- Every enterprise-only validation item is carried into `PUBLIC_DELIVERY_MANIFEST.md` and the non-code internal Agent report.
- The GitHub repository is bootstrapped with an approved documentation baseline; implementation occurs on `agent/mvp-vertical-slice`, not directly on `main`.

## Corrected findings

- Replaced the obsolete `uipro-cli@2.5.0` instruction with the already installed `ui-ux-pro-max` Skill and local search script.
- Explicitly moved LLM Wiki and model-assisted knowledge enrichment out of MVP.
- Selected the production-recommended MCP TypeScript SDK v1.x instead of the pre-stable v2 line.
- Made the public/internal scope boundary explicit so passing public tests cannot be misreported as company-environment compatibility.

## Go/no-go gate

Proceed when this report, the updated plan, and the LLM Wiki deferral pass static checks. Stop only for a reproducible failing baseline, missing required build tool, GitHub authentication failure, or a contradiction that changes the approved architecture.
