---
name: start-ticket
description: Use when beginning implementation from one or more Jira tickets and the requirement must be clarified against repository and Journey context before design or coding.
version: "2.0"
---

# Start Ticket

## When to use
A ticket has no persisted requirement-analysis task, or the human asks to start one.

## Procedure
1. Call `workflow_list_my_tasks`, then reuse an existing matching task or ask the user before creating a duplicate.
2. Call `workflow_get_task_context`. Read ticket text, repository onboarding, Journey/API relationships, relevant code, policies, prior decisions, API compatibility constraints, and release-train context that are actually available.
3. Call `workflow_claim_task` with the current version. Never assume that selecting this skill claims work.
4. Analyze ambiguity Socratically (one focused question at a time) about observable behavior, users, data, failure paths, compatibility, rollout, analytics, accessibility, and manual E2E evidence. Record unresolved items instead of inventing answers.
5. Produce the requirement report from `templates/requirement-contract.md`.
6. Submit with `workflow_submit_artifact`.
7. Ask the human to confirm the exact artifact version. After confirmation, call `workflow_complete_task` to move it to the approval gate.
8. Stop. Do not design, edit code, push a branch, open a PR, or approve on behalf of a person.

## Output contract
Artifact type `REQUIREMENT_REPORT` matching the requirement-contract schema. If onboarding or code evidence is missing, mark `BLOCKED_BY_CONTEXT` and state the smallest evidence needed. A user may explicitly skip a later design approval; record that decision and actor in the workflow via `workflow_task_skip`, but never silently skip this requirement confirmation.
