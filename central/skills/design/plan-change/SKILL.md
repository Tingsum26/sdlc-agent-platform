---
name: plan-change
description: Use to turn an approved design into an implementation plan with ordered repo tasks.
version: "1.0"
---

# Plan Change

## When to use
Design is approved and implementation work must be decomposed.

## Procedure
1. Read the approved design and the dependency DAG.
2. Decompose into repo tasks: repository, base commit, change scope, tests, acceptance.
3. Order tasks by the DAG and by the API-first/native-later rule.
4. Attach rollback and flag notes per task.

## Output contract
Implementation plan artifact. Every repo task references its ticket and base commit; no task may start before design approval or a recorded skip attestation.
