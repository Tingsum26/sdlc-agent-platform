---
name: review-analytics-tagging
description: Use to verify analytics tagging on UI changes: events, parameters, privacy, and testability.
version: "1.0"
---

# Review Analytics Tagging

## When to use
A ticket requires analytics events or a UI change touches tagged flows.

## Procedure
1. Compare implemented events with the ticket tagging requirements.
2. Check event names, parameters, PII exclusion, and test selectors.
3. Flag missing, misnamed, or untestable events.

## Output contract
Tagging findings artifact with severity per finding.
