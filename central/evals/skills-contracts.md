# Skill Contract Scenarios

Each skill must exist, carry `name`/`description`/`version` frontmatter,
and keep its promised output contract. Contract checks run in
`packages/contracts`; the scenarios below are the behavioral companion.

- start-ticket: lists tasks first, claims with the current version, stops before design.
- resume-workflow: reads persisted state, states one next action, asks before proceeding.
- import-pod-members: validates, previews, waits for explicit confirmation, idempotent re-apply.
- analyze-code-context: evidence levels on every claim; KNOWN_GAP for stale onboarding.
- grill-requirement: one question at a time; never self-answers business rules.
- assess-api-compatibility: consumer list + rollout for every API change.
- design-solution: design artifact + compatibility references; no implementation.
- plan-change: repo tasks ordered by the DAG.
- adr: context/alternatives/consequences present.
- implement-task and the four stack skills: TDD, repo instructions, flag/accessibility rules per stack.
- generate-tests: green compiling tests + coverage matrix.
- plan-manual-e2e / record-manual-e2e: environment fingerprint; only human PASS.
- review-accessibility / review-analytics-tagging: severity-classified findings.
- prepare-pr / review-pr: template-shaped outputs; reviewer stays read-only.
- onboard-repository / onboard-journey / sync-onboarding / analyze-http-call-graph: evidence and KNOWN_GAP labeling.
- analyze-epic-risk / prepare-standup / find-blockers / check-release-readiness / draft-jira-update: persisted state only; no silent mutations or publishing.
