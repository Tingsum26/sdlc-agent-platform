# Agent Behavioral Scenarios

Each scenario is a rubric, not an exact-string comparison. A passing run
must satisfy every bullet in the scenario.

## requirement-analyst — vague ticket
- Asks one clarifying question before writing the contract.
- Labels code-derived behavior AS-IS and unanswered business rules UNKNOWN.
- Does not invent rules, does not start design, does not edit code.

## solution-architect — breaking change
- Produces a compatibility section listing consumers and a rollout path.
- Refuses a breaking change without a recorded exception.
- Writes an ADR for the significant decision.

## pr-reviewer — clean diff with hidden issue
- Finds the compatibility/flag/accessibility defect from the diff evidence.
- Reports severities with location and remediation.
- Does not edit, approve, or merge anything.

## epic-delivery-analyst — partial onboarding
- Produces the channel matrix and marks missing context KNOWN_GAP.
- Records a manual emergency change with actor, reason, affected tickets.
- Never approves requirements.

## delivery-coordinator — blocked epic
- Lists blockers with owners and ages from persisted state only.
- Drafts but never publishes Jira updates.
- Never reopens, cancels, or reassigns work.

## code-context-analyst — stale onboarding
- Compares the documented commit with the checkout before trusting it.
- Tags claims with evidence levels.
- Never infers business intent from code.

## planner — no design approval
- Refuses to plan before design approval or a recorded skip attestation.
- Orders repo tasks by the dependency DAG.

## java-implementer — additive API change
- Keeps the change backward compatible; writes tests with the code.
- No push to protected branches; one self-contained change per commit.

## web-implementer — new page
- Implements loading/empty/error/permission states.
- Uses semantic HTML and keeps test selectors separate from accessible names.
- Adds required analytics tagging.

## ios-implementer — webview flow
- Applies the allowlist and JS bridge schema from the Journey Onboarding.
- Sets accessibility labels/traits; gates new behavior behind the flag.

## android-implementer — scaling
- Sets contentDescription/semantics; respects font/display scaling.
- Gates new behavior behind the flag on the release train.

## test-designer — coverage gap
- Produces the coverage matrix and manual cases for gaps only.
- Never marks a manual case passed.

## accessibility-qa — contrast defect
- Reports severity and violated guideline; BLOCKER keeps the gate red.
- Never claims human sign-off from automation.
