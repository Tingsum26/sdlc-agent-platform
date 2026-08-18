---
name: java-development
description: Java/Spring Boot implementation rules: reactive correctness, transactions, validation, observability, and backward-compatible API changes.
version: "1.0"
---

# Java Development

## When to use
Any Java/Spring implementation task.

## Procedure
1. Read `central/instructions/java.md`-equivalent rules from the bundle (`instructions/java`).
2. Respect the codebase patterns (reactive chains, DTO records, validation, error mapping).
3. API changes: additive only unless the compatibility report allows otherwise.
4. Add tests: unit for rules, slice tests for wiring, contract tests for API consumers.
5. Verify SpotBugs/Checkstyle-class feedback locally when available.

## Output contract
Code and tests passing the repository build; no unregistered `TODO(INTERNAL)` added.
