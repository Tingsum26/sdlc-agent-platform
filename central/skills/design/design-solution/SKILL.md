---
name: design-solution
description: Use to produce a cross-repository solution design from an approved requirement contract.
version: "1.0"
---

# Design Solution

## When to use
The requirement contract is approved and a design is needed before planning.

## Procedure
1. Read the approved contract version and affected Repo/Journey Onboarding.
2. Design service boundaries, data model changes, endpoint changes, error handling, observability, flags, and rollback.
3. Draw the sequence/flow in Mermaid in the design artifact.
4. Run `assess-api-compatibility` for every API change.
5. Submit the design artifact and stop for human review.

## Output contract
Solution design matching the solution-design template plus compatibility report references. Never implement in this skill.
