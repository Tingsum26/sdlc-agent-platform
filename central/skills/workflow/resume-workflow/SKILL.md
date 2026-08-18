---
name: resume-workflow
description: Use after a shutdown, context compaction, or machine switch to restore workflow state from persisted artifacts instead of chat history.
version: "2.0"
---

# Resume Workflow

## When to use
The human asks to continue an epic/ticket after an interruption.

## Procedure
1. Call `workflow_list_my_tasks` and `workflow_get_task_context` for the target workflow.
2. For epics, call `workflow_epic_resume` and read open tasks, next actions, and the audit trail.
3. Rebuild a frozen context package: current stage artifact, open questions, relevant Journey subgraph, repository commit, applicable instructions/policies.
4. State what was completed, what is in progress, and the single next action before doing anything.
5. Proceed only after the human confirms the next action.

## Output contract
A short resume summary plus the claimed next task with its expected version. Never re-run completed stages or re-create artifacts that already exist at a newer version.
