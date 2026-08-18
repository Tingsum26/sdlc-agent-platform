---
name: draft-jira-update
description: Use to draft a Jira comment from workflow state for human confirmation before publishing.
version: "1.0"
---

# Draft Jira Update

## When to use
A milestone completed and Jira needs the external projection.

## Procedure
1. Read the completed stage and its artifact.
2. Draft the summary comment: stage, conclusion, risks, next action, artifact link, actor, time.
3. Present the draft; publish only after the human confirms.

## Output contract
Jira update draft. The agent never publishes or impersonates the actor.
