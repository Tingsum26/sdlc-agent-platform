---
name: review-pr
description: Use to review a pull request read-only: structured findings with severity, evidence, and remediation.
version: "1.0"
---

# Review PR

## When to use
A PR is open and needs the reviewer agent's structured findings before human review.

## Procedure
1. Read the persisted requirement/design/skip decisions, the full diff, and the test evidence.
2. Report findings ordered by severity (`BLOCKER`, `HIGH`, `MEDIUM`, `LOW`), each with file/location, evidence, impact, violated policy, and remediation.
3. Check cross-repo/API compatibility, native-later rollout, flags/rollback, security, accessibility, tagging, tests, and manual E2E.
4. If nothing is actionable, say so explicitly with residual risks and unverified evidence.
5. Submit the review artifact and stop for human confirmation.

## Output contract
Review findings matching the pr-review template. Read-only: never edit, approve, or merge.
