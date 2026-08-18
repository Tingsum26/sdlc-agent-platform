---
name: analyze-epic-risk
description: Use to produce an epic risk report from persisted tickets, dependencies, flags, and release-train state.
version: "1.0"
---

# Analyze Epic Risk

## When to use
The Scrum Master asks for an epic risk view.

## Procedure
1. Call `workflow_epic_resume` and read ticket statuses, blockers, and dependencies.
2. Score risks: unresolved deps, stale approval waits, flag/release-train conflicts, long-blocked tasks.
3. Produce the risk report with owner and next action per risk.

## Output contract
Epic risk report artifact. Recommendations only; no workflow mutations.
