# RED/GREEN Eval Scenarios

Run against the workflow service's fake profile. RED = the skill's gate
must fail when its precondition is missing; GREEN = passes after the
precondition is met.

## start-epic
RED: calling `workflow_epic_create` twice with the same epicId fails the second time.
GREEN: create → activate → attach tickets works and the audit trail records each step.

## grill-requirement
RED: a requirement contract with a critical UNKNOWN and no interview report must not pass the stage gate.
GREEN: interview report + contract with resolved questions passes.

## review-pr
RED: a review artifact with no findings and no residual-risk section fails validation.
GREEN: findings with severity/location/evidence/remediation validate.

## import-pod-members
RED: applying a roster without the confirmed flag fails; a duplicate active employee fails.
GREEN: validate → confirmed apply persists the revision and the audit event.
