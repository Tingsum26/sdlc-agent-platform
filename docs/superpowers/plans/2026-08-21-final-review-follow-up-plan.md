# Final Review Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the four load-bearing Important findings left by the whole-branch re-review without weakening the public/local-only boundary.

**Architecture:** Preserve the last-known view payload and render its warning before an empty state. Keep Jira summaries strictly server-authored and omit untrusted workflow identifiers. Make Workflow Task terminal gates type-aware, then drive M7 through only truthful CI/manual-E2E evidence. Make descendant lineage require an unambiguous child-after-parent start time.

**Tech Stack:** TypeScript/VS Code extension, Java 17/Spring Boot, React/Playwright, Windows PowerShell.

**Spec:** `docs/superpowers/plans/2026-08-20-branch-remediation-and-m8-plan.md`; final review package `.superpowers/sdd/2026-08-20-branch-remediation-and-m8-plan/review-1f996bd..ca9d33c.diff`.

## Global Constraints

- All AI reasoning remains user-started local VS Code GitHub Copilot Chat only.
- No Docker, local MongoDB, S3/GridFS, cloud/server Agent, real company endpoint, credential, or company data.
- Add a failing test before every behavioral correction and do not push until review and fresh evidence pass.

## Task 1: Preserve visible refresh warnings for empty cached views

**Files:**

- Modify: `apps/vscode-extension/src/views/myWorkProvider.ts`
- Modify: `apps/vscode-extension/src/views/epicProvider.ts`
- Modify: `apps/vscode-extension/src/views/scrumMasterProvider.ts`
- Modify: `apps/vscode-extension/src/views/ticketProvider.ts`
- Modify: `apps/vscode-extension/test/views.test.ts`

- [ ] Write failing provider tests for a successful empty response followed by a failed refresh; each view must expose its warning before its empty-state item.
- [ ] Run the focused extension test and observe the old empty-only output.
- [ ] Render warning/error tree items before normal empty-state branches while retaining last-known state and accessible empty detail.
- [ ] Re-run focused tests, extension suite, typecheck, and package; commit the focused fix.

## Task 2: Keep Jira projection summaries free of caller-controlled ticket identifiers

**Files:**

- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/jiraprojection/JiraSummaryFactory.java`
- Modify: `apps/workflow-service/src/test/java/dev/sdlc/workflow/api/JiraProjectionIT.java`

- [ ] Write a failing integration test using a ticket identifier shaped as a standalone credential and assert no submitted Jira summary includes it.
- [ ] Run the focused test and observe the identifier in the old summary.
- [ ] Use only fixed server-authored wording and approved artifact metadata in the outbound summary; do not include ticket ID, section title, or body.
- [ ] Re-run focused and workflow-service tests; commit the focused fix.

## Task 3: Make M7 terminal evidence stage-specific

**Files:**

- Modify: `apps/workflow-service/src/main/java/dev/sdlc/workflow/task/TaskTransitionPolicy.java`
- Modify: affected workflow-service task transition tests
- Modify: `apps/web-ui/src/fictionalSdlcDriver.ts`
- Modify: `apps/web-ui/src/fictionalSdlcDriver.test.ts`
- Modify: `e2e/m7-end-to-end.spec.ts`

- [ ] Write failing tests proving requirement/design/PR-review work does not record manual E2E, while the dedicated `MANUAL_E2E` task does; assert terminal states follow the type policy.
- [ ] Run focused tests and observe generic CI/manual evidence is currently emitted for every stage.
- [ ] Define the minimal type-aware path: approval-only stages complete after approval; CI stages complete after CI unless the type is `MANUAL_E2E`; only `MANUAL_E2E` enters and records the manual-E2E gate. Update M7 to follow returned/persisted state and assert service evidence.
- [ ] Re-run Java, Web, and M7 browser tests; commit the focused fix.

## Task 4: Require strict temporal lineage for Windows descendant shutdown

**Files:**

- Modify: `scripts/lib/process-lineage.psm1`
- Modify: `scripts/tests/stop-demo.test.ps1`

- [ ] Write a failing lineage regression for a prospective child whose start time precedes the recorded parent and assert it is never classified as a descendant.
- [ ] Run the focused PowerShell test and observe the 100ms tolerance accepts it.
- [ ] Require `child.StartTime >= parent.StartTime`; apply timestamp normalization only to compare persisted root identity with the actual root process, not to parent-child lineage.
- [ ] Re-run the stop-demo regression repeatedly and the unpiped start/stop port-release smoke; commit the focused fix.

## Task 5: Follow-up evidence and review

- [ ] Run all affected unit, extension, browser, lifecycle and registry verification commands; refresh M8 evidence/manifest counts after new code changes.
- [ ] Independently review the complete follow-up diff for the four original findings and new Critical/Important regressions.
- [ ] Do not push; commit only fresh verification evidence after successful commands and review.

## Plan Self-Review

- Each final-review finding has a focused implementation task and a behavior-specific failing test.
- Task 3 changes workflow policy and M7 together so the UI cannot fabricate evidence incompatible with server state.
- Task 5 repeats evidence after behavior changes and keeps the Mongo/seven-repository limitations explicit.
