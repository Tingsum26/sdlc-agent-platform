---
name: import-pod-members
description: Use when a Pod member roster (CSV/JSON) must be validated and imported into the workflow service for assignment routing.
version: "2.0"
---

# Import Pod Members

## When to use
A roster file exists (see `assets/pod-members-template.csv`) and members must be imported without direct database access.

## Procedure
1. Read the CSV/JSON and map rows to the membership schema in `references/import-contract.md`.
2. Validate locally: header, required fields, duplicate employee IDs, active rows, and unknown Pod IDs — stop on any failure with the failing row.
3. Call `workflow_validate_pod_roster` (DRY_RUN). If it fails, report the server-side error and stop.
4. Present a redacted preview (counts of add/update/no-change, not full personal data) and ask the human to confirm.
5. Only after explicit confirmation call `workflow_import_pod_roster` with `confirmed: true`.
6. Re-apply must be idempotent: re-read the current roster revision first.

## Output contract
The saved roster revision plus an import report (added/updated/unchanged counts, no personal data). Never connect to MongoDB or Jira directly; never import without the human confirmation.
