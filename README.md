# Local Copilot SDLC Platform

Public, infrastructure-free MVP for coordinating a software-delivery workflow around user-initiated GitHub Copilot Agent sessions in VS Code.

## Non-negotiable boundaries

- All AI reasoning is initiated by a user in local VS Code GitHub Copilot.
- Workflow Service, Workflow MCP, VSIX, Web UI, CI, and persistence adapters contain no model client.
- The public demo uses Fake repositories and fictional data.
- Company MongoDB, Jira, Confluence, GitHub Enterprise, Jenkins, Splunk, SSO, and Teambook integration are internal validation work.
- No Docker, Compose, local MongoDB, Embedded Mongo, Testcontainers, MinIO, or cloud Agent is required.
- LLM Wiki, vector search, cross-repository scanning, and enterprise adapters are post-MVP extensions.

## Current contents

- v2 architecture and human-readable HTML design report;
- executable seven-task MVP implementation plan;
- public-to-internal handoff and completion-report templates;
- pre-implementation review and locked decisions;
- central `importing-pod-members` Agent Skill, contract, template, and evals.

## Start here

1. Read `docs/reviews/2026-08-16-pre-implementation-review.md`.
2. Read `docs/superpowers/specs/2026-08-15-local-copilot-sdlc-platform-v2-design.html`.
3. Follow `docs/superpowers/plans/2026-08-14-public-sdlc-mvp-implementation.md`.

The implementation is developed on an isolated feature branch. Do not treat passing public tests as proof of compatibility with a company environment.
