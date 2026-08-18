---
name: record-manual-e2e
description: Use to record QA manual E2E results with evidence and the exact environment fingerprint.
version: "1.0"
---

# Record Manual E2E

## When to use
QA executed the manual plan and results must be recorded.

## Procedure
1. Read the manual plan and the environment/build fingerprint.
2. Record per case: `PASS`/`FAIL`/`BLOCKED`/`NOT RUN`, actual result, evidence references, defect links, executor role.
3. Submit the result artifact through the workflow.

## Output contract
Manual E2E result artifact. Only a human QA result is recorded; the agent never fabricates a PASS.
