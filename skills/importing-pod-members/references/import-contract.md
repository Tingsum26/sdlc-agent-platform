# Pod membership import contract

## Input

CSV header:

```text
employeeId,displayName,email,podId,role,capabilities,githubLogin,status
```

| Field | Required | Rules |
|---|---:|---|
| `employeeId` | yes | External identifier; trim; never use as the database primary key |
| `displayName` | yes | Human-readable snapshot |
| `email` | recommended | Corporate email; blank is allowed when policy permits |
| `podId` | yes | Must exist in the active central routing bundle |
| `role` | yes | MVP claimed role; not authorization |
| `capabilities` | yes | Semicolon-separated approved capability IDs |
| `githubLogin` | no | Blank means the person may not have onboarded VSIX |
| `status` | yes | `ACTIVE` or `INACTIVE` |

Use one row per person/Pod membership. CSV must be UTF-8 and contain no formulas. Reject cells beginning with `=`, `+`, `-`, or `@` when a spreadsheet could interpret them as formulas.

## Workflow MCP tools

### `pod_members_import_dry_run`

Input:

```json
{
  "source": "authorized local file or uploaded content reference",
  "format": "CSV",
  "routingVersion": "bundle version shown by VSIX",
  "idempotencyKey": "client-generated UUID"
}
```

Required result:

```json
{
  "batchId": "batch identifier",
  "dryRunToken": "short-lived opaque token",
  "contentHash": "sha256",
  "routingVersion": "resolved version",
  "counts": {
    "addPeople": 0,
    "addMemberships": 0,
    "updateMemberships": 0,
    "deactivateMemberships": 0,
    "noChange": 0,
    "notOnboarded": 0,
    "conflicts": 0
  },
  "blockingCodes": [],
  "warningCodes": [],
  "correctionReportRef": "optional authorized reference",
  "expiresAt": "timestamp"
}
```

Blocking codes include `UNKNOWN_POD`, `DUPLICATE_IDENTITY_CONFLICT`, `INVALID_CAPABILITY`, `INVALID_STATUS`, `MISSING_REQUIRED_FIELD`, `CSV_FORMULA_RISK`, and `STALE_ROUTING_VERSION`.

### `pod_members_import_apply`

Input must repeat `batchId`, `dryRunToken`, `contentHash`, and `routingVersion`, plus a new idempotency key. The server rejects an expired token, changed content, changed routing version, unresolved blocker, or repeated key with different content.

### `pod_members_import_status`

Query by batch ID or apply idempotency key. Terminal states are `APPLIED`, `REJECTED`, `FAILED`, and `CANCELLED`.

## Import Report

Return batch ID, source hash, actor, timestamps, routing version, counts, status, warnings, masked conflicts, created `principalId` references, inactive memberships, `ASSIGNEE_NOT_ONBOARDED` count, and correction-report reference. Do not retain or echo the raw roster.
