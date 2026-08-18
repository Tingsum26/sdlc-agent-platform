---
name: generate-tests
description: Use to generate unit, integration, contract, and UI tests from the requirement contract and design.
version: "1.0"
---

# Generate Tests

## When to use
Implementation exists and automated coverage must be produced.

## Procedure
1. Map acceptance criteria to test layers (unit/slice/contract/UI).
2. Generate tests per repository conventions; include failure-path and flag-off/flag-on cases.
3. Run the generated tests and fix them until green.
4. Record the coverage matrix in the test artifact.

## Output contract
Compiling, green tests plus the coverage matrix. Never claim a manual E2E case as automated.
