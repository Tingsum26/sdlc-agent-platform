---
name: prepare-pr
description: Use when implementation and local tests are complete and a developer needs evidence, risk, compatibility, and reviewer context prepared before manually opening a pull request.
---

# Prepare PR

Prepare evidence; do not publish changes.

1. Call `workflow_get_task_context` and require the approved requirement/design decisions or an explicitly recorded human skip decision.
2. Inspect the diff against the target branch. Map every changed file to acceptance criteria, API/mobile compatibility, feature flags, data migration, observability, security, accessibility/tagging, and affected Journey calls.
3. Run the repository's documented deterministic tests. Ask Copilot to generate missing unit, integration, contract, and regression cases; keep manual E2E steps for behavior that cannot be automated.
4. Report exact commands and results. Do not claim a test passed without current evidence.
5. Produce a PR report containing: summary, ticket/Epic links, artifact versions, repository scope, contract changes, backward-compatibility proof, flags/rollback, test matrix, manual E2E for QA, accessibility/tagging, logs/metrics, known risks, reviewer focus, and unresolved blockers.
6. Submit the report with `workflow_submit_artifact`, request human confirmation, and stop at the approval gate.

Never push a branch, open or merge a PR, change Jira, or approve the report automatically. After a human authorizes publication, the developer uses the organization's normal GitHub process. Any missing required evidence is a blocker, not a warning to hide.
