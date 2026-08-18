---
name: prepare-pr
description: Use when code changes are complete and a pull request must be prepared with evidence, tests, and a structured description.
version: "2.0"
---

# Prepare PR

## When to use
Implementation and generated tests are done locally and CI-relevant evidence must be collected.

## Procedure
1. Run the repository build and test commands; capture exact outputs.
2. Run the local candidate scan (if available) and record API/graph changes versus the base commit.
3. Write the PR description from `templates/pr-description.md`: change summary, compatibility statement, flag/rollout notes, test evidence, manual E2E status, and affected Journey consumers.
4. Verify no secret files, no debug output, and no unregistered `TODO(INTERNAL)` markers are added by this change.
5. Create the PR through the approved channel and record the PR link in the workflow.

## Output contract
PR description matching the pr-description template plus the evidence block. Never push to protected branches or merge.
