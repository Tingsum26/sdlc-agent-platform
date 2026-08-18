---
name: onboard-repository
description: Use to generate the repository onboarding package: architecture summary, entry points, build/test commands, and the machine-readable context file.
version: "1.0"
---

# Onboard Repository

## When to use
A repository enters the platform or its onboarding is missing.

## Procedure
1. Scan structure, build files, entry points, tests, and deployment notes.
2. Produce `docs/architecture/overview.md`, module notes, build/test/run commands, and `.agent-context.yaml`.
3. Mark each claim with the source commit; mark unknowns `KNOWN_GAP`.
4. Submit the onboarding artifact for human review.

## Output contract
Repository onboarding artifact plus the context file. Never claim a full call graph when only static reading was possible.
