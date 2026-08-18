---
name: prepare-standup
description: Use to prepare a stand-up summary from persisted state: progress, blockers, next actions per ticket.
version: "1.0"
---

# Prepare Stand-up

## When to use
Before a stand-up meeting.

## Procedure
1. Read ticket and repo-task state with `workflow_epic_resume` and note observation times.
2. Summarize per ticket: done, doing, blocked, next.
3. Note stale observations and who has the next action.

## Output contract
Stand-up summary artifact. Never invent progress not present in persisted state.
