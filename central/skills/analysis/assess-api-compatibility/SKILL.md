---
name: assess-api-compatibility
description: Use for any API request/response/header change to determine breaking impact and the required rollout strategy.
version: "1.0"
---

# Assess API Compatibility

## When to use
A design or diff touches an API consumed by Web/iOS/Android clients.

## Procedure
1. Diff the API surface: fields, types, enums, required-ness, status codes, headers, defaults.
2. List every consumer (repo, app version, flag state) from the Journey Onboarding and graph.
3. Classify each change additive vs breaking. Breaking changes require a parallel version, compatibility adapter, or an explicit exception with evidence.
4. Specify the rollout: API first, web follows, native on the release train behind a flag, kill switch, and deletion condition.

## Output contract
Compatibility report artifact with per-consumer impact and rollout steps. Breaking changes without a documented exception block the merge gate.
