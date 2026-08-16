---
name: importing-pod-members
description: Use when importing, updating, previewing, or deactivating Pod team membership from CSV or JSON through Workflow MCP, especially when employee identities, unknown pods, duplicates, or people without VSIX onboarding must be handled safely.
---

# Importing Pod Members

## Overview

Import Pod rosters through Workflow MCP with an auditable preview. Never mutate membership before a successful `DRY_RUN` and explicit confirmation of the exact batch.

Read [references/import-contract.md](references/import-contract.md) before operating. Start from [assets/pod-members-template.csv](assets/pod-members-template.csv) when a template is needed.

## Workflow

1. Inspect the CSV/JSON header and required fields. Do not echo unmasked roster rows into chat.
2. Call Workflow MCP `pod_members_import_dry_run` with the source and an idempotency key. Database and Jira access are outside this Skill.
3. Present the returned batch ID, content hash, add/update/deactivate/no-change counts, conflicts, warnings, and not-onboarded count.
4. Stop without `APPLY` when the result contains `UNKNOWN_POD`, conflicting duplicate employee IDs, invalid capabilities/status, missing required fields, or a stale routing version.
5. Ask for explicit confirmation naming the batch ID and summarized changes. A general “continue” from before the preview is not confirmation.
6. Call `pod_members_import_apply` with the dry-run token, batch ID, content hash, routing version, and a new idempotency key.
7. Call `pod_members_import_status` until the batch reaches a terminal state. Do not blindly retry an uncertain apply; query its status first.
8. Return the versioned Import Report and correction file reference. Mask email and employee IDs in chat output.

## Required behavior

| Condition | Result |
|---|---|
| Valid person without GitHub/VSIX | Import directory person; report `ASSIGNEE_NOT_ONBOARDED` |
| `status=INACTIVE` | End the membership; preserve person and audit history |
| Same person in multiple Pods | Allow one row per membership |
| Conflict or `UNKNOWN_POD` | Return correction report; never apply |
| Workflow MCP import tools unavailable | Stop with `WORKFLOW_MCP_IMPORT_UNAVAILABLE` |
| Apply response lost | Query status by idempotency key; never resubmit blindly |

## Data boundaries

- Use Workflow MCP exclusively; do not access MongoDB, Jira, or Teambook from this Skill.
- Never commit roster files, copy them into the central repository, or place raw rows in logs, Jira comments, prompts, or reports.
- Do not create a local database, container, or background service.
- Keep only the server-provided import manifest, hash, counts, actor, timestamps, and row-level result references.

## Example

User: “Import this Pod CSV.”

Respond first with the `DRY_RUN` preview. After explicit confirmation, apply that exact batch and return its Import Report. If three rows reference an unknown Pod, stop and return those row numbers plus `UNKNOWN_POD` without exposing unrelated rows.

## Common mistakes

- Applying after local CSV inspection without a server dry run.
- Treating blank GitHub login as an invalid employee.
- Deleting a person when only one membership becomes inactive.
- Retrying apply after a timeout without checking batch status.
- Publishing the raw roster as Jira evidence.
