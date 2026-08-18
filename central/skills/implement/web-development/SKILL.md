---
name: web-development
description: Web implementation rules: component reuse, states, semantics, accessibility, tagging.
version: "1.0"
---

# Web Development

## When to use
Any web frontend implementation task.

## Procedure
1. Read `instructions/web` and the design-system guidance in the repository.
2. Reuse existing components; implement loading/empty/error/permission states.
3. Use semantic HTML, labels, roles, and visible focus; keep test selectors separate from accessible names.
4. Add analytics tagging where the ticket requires it.
5. Run the local lint/build/tests before committing.

## Output contract
Buildable web changes with tests; WCAG 2.2 AA baseline respected.
