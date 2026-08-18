# Pod Roster Import Contract

Input source: a CSV or JSON roster file. The client converts CSV rows to the
membership shape below before calling the Workflow MCP.

## Membership shape (one row per member)

| Field | Required | Notes |
|---|---|---|
| `membershipId` | yes | Stable id, e.g. `MEM-<employeeId>` |
| `employeeId` | yes | Unique per ACTIVE member within one journey |
| `principalId` | yes | Workbench identity, e.g. `PRINCIPAL-<employeeId>` |
| `displayLabel` | yes | Display name |
| `role` | yes | e.g. `SCRUM_MASTER`, `DEVELOPER`, `QA` |
| `journeyId` | yes | Must match the roster journey for every row |
| `active` | yes | `true`/`false` |
| `effectiveFrom` | yes | ISO date, e.g. `2026-01-01` |
| `effectiveTo` | no | ISO date or null |
| `aliases` | yes | Array of alias strings; `[]` when none |

CSV template header: `employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom`
(`membershipId` is derived as `MEM-<employeeId>`, `aliases` as `[]`).

## Roster envelope

```json
{
  "journeyId": "ACCOUNT_OPENING",
  "expectedRevision": 0,
  "memberships": [ /* rows from the table above */ ]
}
```

## Tool flow

1. `workflow_validate_pod_roster` — DRY_RUN only; returns `{"valid": true, ...}` or an error naming the failing constraint (duplicate active employee, journey mismatch, duplicate membership id).
2. Human confirms a redacted preview (added/updated/no-change counts, never personal data).
3. `workflow_import_pod_roster` with `confirmed: true` and the CURRENT roster revision — re-read the revision before re-apply so imports stay idempotent.

## Rules

- Never connect to MongoDB or Jira directly.
- Never import without the human confirmation.
- Never log or echo personal data.
