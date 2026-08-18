# Policy Vocabulary

Policies are machine-readable JSON consumed by the workflow policy loader.

## Effect values
- `BLOCK` — the gated action may not proceed.
- `WARN` — the action proceeds with a recorded warning.
- `REQUIRE_EVIDENCE` — the action proceeds only when the named evidence is attached.

## Event vocabulary
- Lifecycle: `commit`, `artifact-submit`, `stage-advance`, `stage-skip`, `merge-gate`.
- Domain deltas: `api-change`, `native-change`, `flag-introduction`, `pod-assignment`, `manual-e2e-result`, `jira-comment`.
- Review steps: `pr-review`, `tagging-review`.
A policy may listen to a single event or an array of events.

## Intended stage-skip semantics
Per the approved design, ANY stage may be skipped when a SkipAttestation is recorded
(reason, discussed-with, actor, role) and the audit trail captures it. The earlier
v1 restriction (DESIGN-only) was superseded; this is a deliberate loosening.
