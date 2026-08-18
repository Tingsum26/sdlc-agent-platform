---
name: check-release-readiness
description: Use to check release readiness: merged state, flags, release train, manual E2E, and rollback posture.
version: "1.0"
---

# Check Release Readiness

## When to use
A release window approaches.

## Procedure
1. Read ticket statuses (MERGED/RELEASED/FLAG_ENABLED/E2E_VERIFIED), CI, and manual E2E results via `workflow_epic_resume`.
2. Verify the flag plan, native release-train window, and rollback rule.
3. List open gates and who must act.

## Output contract
Release readiness artifact with open gates. No approval is granted by this skill.
