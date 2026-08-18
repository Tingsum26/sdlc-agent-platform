---
name: analyze-code-context
description: Use to build an AS-IS evidence pack of current behavior from code, tests, OpenAPI, and Onboarding before requirement or design work.
version: "1.0"
---

# Analyze Code Context

## When to use
A requirement or design needs current-behavior facts, and Onboarding freshness is unknown.

## Procedure
1. Read the Journey Onboarding to locate screens/endpoints; check its source commit against the current checkout.
2. Inspect the affected controllers, clients, validations, flags, and tests directly.
3. Collect OpenAPI/AsyncAPI where present.
4. Classify every claim: `TEST_VERIFIED`, `CODE_VERIFIED`, `DOC_STATED`, `AI_INFERRED`, `UNRESOLVED`.
5. Mark stale or missing Onboarding as `KNOWN_GAP` with the smallest evidence needed to close it.

## Output contract
Evidence pack artifact: claims with file/symbol/commit references, evidence level, and gap list. Never infer business intent from code.
