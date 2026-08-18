---
name: plan-manual-e2e
description: Use to create QA manual E2E plans for scenarios automation cannot cover, with environment and evidence requirements.
version: "1.0"
---

# Plan Manual E2E

## When to use
Automated coverage has gaps: cross-repo flows, permissions, real devices, network degradation, or release verification.

## Procedure
1. Read the acceptance criteria and the automated coverage matrix.
2. List scenarios automation cannot cover; prioritize by risk.
3. For each case: environment, build fingerprint, roles, steps, expected result, evidence, cleanup, and a reason it is manual.
4. Submit the manual E2E plan.

## Output contract
Manual E2E plan artifact. You never mark a case passed.
