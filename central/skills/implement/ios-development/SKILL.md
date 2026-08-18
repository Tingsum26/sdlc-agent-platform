---
name: ios-development
description: iOS implementation rules: accessibility, Dynamic Type, WebView hybrid boundaries, flag gating.
version: "1.0"
---

# iOS Development

## When to use
Any iOS/Swift implementation task.

## Procedure
1. Read `instructions/ios` and the Journey Onboarding hybrid section.
2. Set accessibility labels/hints/traits; support Dynamic Type and Reduce Motion.
3. WebView calls: allowed domains, JS bridge parameter schema, return-to-native behavior.
4. New behavior ships behind the native flag and follows the release train.
5. Build and run unit/UI tests before committing.

## Output contract
Buildable iOS changes with tests; no API breaking changes; flag-gated new behavior.
