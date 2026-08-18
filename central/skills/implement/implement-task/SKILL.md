---
name: implement-task
description: Use to execute one repo task from an approved implementation plan with TDD and frequent commits.
version: "1.0"
---

# Implement Task

## When to use
A repo task is assigned and its acceptance is clear.

## Procedure
1. Read the task, its acceptance, and the repository instructions.
2. Write the failing test first, watch it fail, implement minimally, watch it pass, then refactor.
3. Run the repository build/tests after each self-contained change.
4. Commit per change with a message tied to the ticket.
5. Update the repo task state when the change is complete.

## Output contract
Commits plus a completed repo-task record. Never push to protected branches; never skip the test step.
