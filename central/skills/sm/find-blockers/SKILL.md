---
name: find-blockers
description: Use to find and classify blockers across epic tickets and repo tasks.
version: "1.0"
---

# Find Blockers

## When to use
The Scrum Master asks what is blocking the epic.
Scope: the immediate operational blocker list; use `analyze-epic-risk` for the broader scored risk view.

## Procedure
1. Using `workflow_epic_resume`, list BLOCKED tickets/tasks, unresolved dependencies, waiting approvals, and failed CI.
2. Classify each by owner and age.
3. Draft blocker notes for the human to confirm before any Jira write.

## Output contract
Blocker report artifact. Confirmations happen in the UI, never silently.
