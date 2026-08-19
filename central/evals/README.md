# Evals

Behavioral scenarios live in `agents-behavior.md`, `skills-contracts.md`, and
`red-green-scenarios.md`. Each RED/GREEN scenario is pinned by an automated
test in this repository where the behavior is deterministic:

| Scenario | Pinned by |
|---|---|
| start-epic RED (duplicate epic) | `EpicWorkflowServiceTest.rejectsDuplicateEpicIds` |
| start-epic GREEN (create → activate → attach) | `EpicWorkflowIT.walksTheFullEpicScenarioWithChangeAndSkip` |
| grill-requirement RED (critical UNKNOWN blocks stage) | Manual: requirement-analyst duty + `central/evals/agents-behavior.md` (no automated stage-gate yet) |
| grill-requirement GREEN (interview report resolves) | Manual rubric (documented, not automatable without a live Copilot) |
| review-pr RED (findings without residual risks) | `central-bundle.test.ts` "review-pr mandates residual risks" (this repo) |
| review-pr GREEN (findings validate) | Manual rubric |
| import-pod-members RED (unconfirmed apply rejected) | `apps/workflow-mcp/test/internalReadinessTools.test.ts` "requires explicit confirmation before Pod persistence" (`workflow_import_pod_roster` requires `confirmed: true`) |
| import-pod-members GREEN (validate → confirmed apply) | `InternalReadinessControllerIT.exposesNonGithubIdentityImportsPodAndAssignsTicket` |

Rows marked Manual require a live Copilot session and are `TODO(INTERNAL)` for
the internal agent to execute on the company network; the public side pins
everything deterministic.
