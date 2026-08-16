---
name: resume-workflow
description: Use when a persisted SDLC task was interrupted, the workstation restarted, ownership may have changed, or the user needs the exact next valid step.
---

# Resume Workflow

Server state is authoritative; chat history is supporting context only.

1. Call `workflow_list_my_tasks` and ask the user to choose if more than one task matches.
2. Call `workflow_get_task_context` for the selected task. Report task status, current version, owner/lease, latest artifact and approval, blockers, CI/manual E2E state, and the next allowed transition.
3. If another active lease exists, do not take over. Ask the human owner to release it or wait for expiry.
4. If the task is claimable, call `workflow_claim_task` with the exact current version.
5. Load the approved or latest artifact and continue only the unfinished stage. Do not regenerate completed analysis or design unless the user records a change request.
6. Before any state-changing MCP call, show the intended transition and ask for human confirmation. If the version changed, refresh context and present the conflict; never retry blindly.
7. Submit only a new immutable artifact version, then stop at the next confirmation or approval gate.

When context cannot be restored, output `RESUME_BLOCKED`, the correlation ID, and the specific missing server evidence. Never infer a completed gate from local files alone.
