# Task 1 report: safe server-generated Jira projection

## Outcome

The `/api/v1/jira-drafts` request contract is now `JiraProjectionRequest(ticketId, milestoneId, artifactId, artifactVersion)`. It no longer accepts a caller-provided summary. The controller loads the persisted ticket, exact artifact version, and producing workflow task; requires the artifact to be approved; and rejects a task whose ticket scope differs from the requested ticket.

`JiraSummaryFactory` constructs the persisted Jira summary from only the ticket ID, artifact type, approved state, and title of an allowlisted metadata section (`summary`, `overview`, or `title`). It never reads artifact bodies or actor metadata. It replaces control characters, URLs, email addresses, and secret-like key/value values, normalizes whitespace, and limits the result to 500 characters. `JiraProjectionService` enforces that upper bound before persisting.

The fake Jira client/profile remains unchanged; no real integration was introduced.

## Focused test coverage

`JiraProjectionIT` now covers:

- unknown ticket rejection;
- unknown artifact rejection;
- rejection when the artifact's producing task belongs to a different ticket;
- a successful projection backed by an approved persisted artifact, containing server-generated metadata but not artifact body text;
- rejection of the former free-text-only request shape; and
- the pre-existing mock CI/ticket advancement scenario.

## TDD evidence

### RED

Command:

```powershell
.\mvnw.cmd -q -pl apps/workflow-service test -Dtest=JiraProjectionIT
```

Before production changes, the focused run exited `1` with `Tests run: 5, Failures: 3`. The failures were the intended contract failures:

- `createsAServerGeneratedProjectionWithoutCallerTextOrArtifactBody`: expected `201`, got `400`, because the former request did not recognize `artifactId`.
- `rejectsAnUnknownArtifact`: expected `404`, got `400`, for the same missing artifact-request contract.
- `rejectsTheOldFreeTextOnlyApiShape`: expected `400`, got `201`, proving the old API still accepted and projected caller free text.

### GREEN

Command:

```powershell
.\mvnw.cmd -q -pl apps/workflow-service test -Dtest=JiraProjectionIT
```

After the implementation, the focused run exited `0` (six tests). The required command was rerun after preserving the existing CI test; it also exited `0`.

Additional module verification:

```powershell
.\mvnw.cmd -q -pl apps/workflow-service test
```

This exited `0`. Its output includes expected best-effort fake Splunk transport warnings from existing tests; they do not fail the suite.

## Scope and concerns

- Only Task 1 production/test files plus this report are included in the commit.
- The old request shape is intentionally rejected at JSON deserialization, so a `summary` property cannot enter the controller or reach the Jira outbox.
- The fake profile's intentionally unavailable Splunk scenario logs a warning in the preserved CI integration test. This was pre-existing best-effort behavior and is non-blocking.

## Review fix round 1: compound secret-like keys

### RED

After adding `redactsCompoundSecretKeysFromAllowlistedArtifactTitles`, the focused command below exited `1` with `Tests run: 7, Failures: 1`:

```powershell
.\mvnw.cmd -q -pl apps/workflow-service test -Dtest=JiraProjectionIT
```

The response summary exposed `client_secret=COMPOUND_SECRET_VALUE`; the assertion correctly reported that the summary still contained `COMPOUND_SECRET_VALUE`.

### GREEN

The sanitizer now recognizes underscore/hyphen compound prefixes for the secret-like keys (including `client_secret`, `access_token`, and `private_key`). The same focused command exited `0` with all seven `JiraProjectionIT` tests passing. The fake-profile Splunk best-effort warning remains expected and non-failing.
