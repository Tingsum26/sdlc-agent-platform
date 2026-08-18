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
3. Draft the journey manifest content and the HTML report skeleton; the human commits them.
4. Mark missing channels `KNOWN_GAP`.

## Output contract
Journey onboarding draft with evidence per edge, for the human to commit. Incomplete input is allowed only with explicit gap labels.
