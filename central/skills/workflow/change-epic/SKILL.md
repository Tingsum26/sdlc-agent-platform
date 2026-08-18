---
name: change-epic
description: Use to record an emergency change against an active epic with dual-role approval.
version: "1.0"
---

# Change Epic

## When to use
A significant change arrives after epic analysis and must be versioned, not silently overwritten.

## Procedure
1. Call `workflow_epic_create_change_request` with reason, urgency, description, and affected tickets.
2. Present the DRAFT change request; do not approve it yourself.
3. Approval requires both BUSINESS_OWNER and TECHNICAL_OWNER roles; after approval the affected tickets are flagged for confirmation.
4. Record the change in the audit trail; never overwrite the approved requirement contract in place.

## Output contract
A change request at DRAFT or APPROVED with affected tickets flagged. Never self-approve.
