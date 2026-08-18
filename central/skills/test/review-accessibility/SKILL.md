---
name: review-accessibility
description: Use to review accessibility on web/iOS/Android changes against WCAG 2.2 AA, VoiceOver, TalkBack, and scaling baselines.
version: "1.0"
---

# Review Accessibility

## When to use
A UI-affecting change is under review.

## Procedure
1. Perform static inspection of semantic structure, focus order, labels/roles, contrast, and scaling; actual VoiceOver/TalkBack output is human-verified.
2. Check tagging correctness (test tags vs accessible names).
3. Classify findings `BLOCKER`/`HIGH`/`MEDIUM`/`LOW` with the violated guideline and remediation.

## Output contract
Accessibility findings artifact. Automation findings never replace human QA sign-off; BLOCKER keeps the merge gate red.
