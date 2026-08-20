# DeepSeek Continuation Import and Review

**Date:** 2026-08-20  
**Reviewed range:** `76ffffa..8f556e4`  
**Branch state:** 14 commits ahead of `origin/agent/mvp-vertical-slice`; none of M6/M7 is pushed.

## Imported decisions

1. The approved seven-public-repository target remains the destination.
2. The user subsequently approved a run-first phase: make a fictional public monorepo runnable through M1–M8 before repository splitting or internal replacement.
3. The run-first phase remains local-only, no Docker, no cloud Agent, no model calls outside interactive Copilot Chat, and only fictitious data.
4. M1 through M5 are reported complete by the imported session. M6 and M7 are locally committed. M8 is not implemented.

## Review outcome

Do not push `8f556e4` yet. The branch contains one Critical and several Important defects. The Critical and the two M7/Repo Task defects must be fixed before M8 can be claimed complete. The remaining scope items are classified below rather than silently accepted.

| ID | Severity | Finding | Disposition |
|---|---|---|---|
| R-01 | Critical | Jira draft endpoint accepts and publishes caller-controlled free text, bypassing the safe-summary boundary. | Fix before push. Project only a bounded server-generated summary from a ticket and persisted artifact. |
| R-02 | Important | `mongo` profile still wires M2–M4 aggregates to in-memory stores. | Intended run-first limitation, already represented by `INTERNAL-AUD-001`; retain as `PARTIAL` until the post-M8 internal/persistence phase. Do not claim durable Mongo workflow state. |
| R-03 | Important | Repo Tasks have no public transition endpoint, so they remain `PLANNED`. | Fix before push. Add API, MCP, tests and UI/state evidence. |
| R-04 | Important | M7 labels four requirement-analysis tasks as design/implementation/test and never creates a Repo Task. | Fix before push. Drive actual stage-specific tasks and a Repo Task lifecycle. |
| R-05 | Important | Bundle installer dereferences bundle symlinks after lexical path checking. | Fix before push. Reject symlinks and test the rejection. |
| R-06 | Important | Ticket and Scrum Master views are fixed to `EPIC-M2-1`. | Fix before push. Resolve a selected/current Epic from the live list and preserve an empty state. |
| R-07 | Minor | Hook no-op command uses POSIX-only syntax on Windows. | Fix before push because Windows is a public-run target; use a bundled Node no-op hook shim. |
| R-08 | Minor | The target seven-repository artifacts do not exist yet. | Expected sequencing limitation of run-first phase; repository split stays pending after M8. |

## Verified root causes

- `EpicController#createJiraDraft` forwards `JiraDraftRequest.summary` directly to `JiraProjectionService.enqueue`; `JiraProjectionService#attempt` forwards it to the Jira client unchanged.
- `RepoTaskService#transition` exists, but `EpicController` and Local MCP expose only Repo Task creation/listing.
- `FictionalSdlcDriver` uses `/workflows/from-ticket` four times; that route creates requirement-analysis tasks and the driver never creates a Repo Task.
- `safeResolve` is lexical only, while `bundleInstaller` uses `cp(..., { dereference: true })` for skill and directory copies.
- `TicketProvider` and `ScrumMasterProvider` call their client with a constant demo Epic ID rather than deriving an active selection.

## Required remediation order

1. Secure Jira projection and bundle installation, including Windows-safe hooks.
2. Make Repo Task lifecycle reachable and make M7 exercise it honestly.
3. Remove fixed demo-Epic selection from VSIX views.
4. Complete M8 registry/CI/handoff, carrying Mongo persistence as a clearly tracked internal limitation rather than a false completion claim.
5. Run all gates, review the full unpushed range, then decide push scope.

## Knowledge-graph limitation

`.understand-anything/knowledge-graph.json` is absent in this worktree. The review therefore used direct source and commit inspection; no graph-based impact overlay was generated.

