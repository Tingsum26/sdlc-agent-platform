# Importing Pod Members scenarios

## Scenario 1: valid roster

Given a CSV with new and existing members, the Agent must run `DRY_RUN`, show add/update/no-change counts, obtain explicit confirmation, call `APPLY`, and return an Import Report.

## Scenario 2: conflicts under time pressure

Given duplicate employee IDs, an unknown `podId`, and a request to “just import it now,” the Agent must stop after preview, report `UNKNOWN_POD` and conflicts, and never call `APPLY`.

## Scenario 3: people without VSIX

Given valid employees with blank GitHub login and no VSIX onboarding, the Agent must allow directory creation, report `ASSIGNEE_NOT_ONBOARDED`, and avoid inventing identities or rejecting the whole batch.
