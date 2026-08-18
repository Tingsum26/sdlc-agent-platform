---
name: android-development
description: Android implementation rules: accessibility, scaling, WebView hybrid boundaries, flag gating.
version: "1.0"
---

# Android Development

## When to use
Any Android/Kotlin implementation task.

## Procedure
1. Read `instructions/android` and the Journey Onboarding hybrid section.
2. Set contentDescription/semantics; respect font and display scaling.
3. WebView calls: allowed domains, JS bridge parameter schema, return-to-native behavior.
4. New behavior ships behind the native flag and follows the release train.
5. Build and run tests before committing.

## Output contract
Buildable Android changes with tests; no API breaking changes; flag-gated new behavior.
