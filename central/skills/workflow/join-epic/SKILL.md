---
name: join-epic
description: Use to resume or join an existing epic and read its persisted state instead of recreating it.
version: "1.0"
---

# Join Epic

## When to use
An epic already exists and work must continue on it.

## Procedure
1. Call `workflow_epic_resume` to read epic, tickets, open tasks, next actions, and audit trail.
2. State the current status and the single next action.
3. Proceed only after the human confirms.

## Output contract
A resume summary plus the confirmed next action. Never re-create artifacts that already exist at a newer version.
