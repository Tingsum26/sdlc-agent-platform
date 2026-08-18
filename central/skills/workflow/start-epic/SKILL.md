---
name: start-epic
description: Use to create or activate an Epic workflow and attach its channel tickets before per-ticket analysis.
version: "1.0"
---

# Start Epic

## When to use
An epic (Jira epic or manual emergency change) must enter the workflow.

## Procedure
1. Call `workflow_epic_create` with the epic id, title, and journey.
2. Call `workflow_epic_activate` with the returned version.
3. Attach API/WEB/IOS/ANDROID tickets with `workflow_epic_attach_ticket`.
4. For a manual emergency change, record reason, urgency, affected tickets, and actor before creating anything.
5. Stop and hand off to the epic delivery analyst for intake.

## Output contract
An ACTIVE epic with its ticket matrix persisted and audit trail entries. Never create a duplicate epic; never invent ticket contents.
