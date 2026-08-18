---
name: onboard-journey
description: Use to build a Journey onboarding: screens, API calls, payload schemas, hybrid boundaries, and release policy across web/iOS/Android/API.
version: "1.0"
---

# Onboard Journey

## When to use
A Journey is new or incomplete.

## Procedure
1. Collect the channel repositories, screens, API calls, headers, flags, and release policy.
2. Ask the human for the hybrid type (in-app WebView vs external browser) instead of assuming.
3. Produce the journey manifest and the HTML report skeleton.
4. Mark missing channels `KNOWN_GAP`.

## Output contract
Journey onboarding artifact with evidence per edge. Incomplete input is allowed only with explicit gap labels.
