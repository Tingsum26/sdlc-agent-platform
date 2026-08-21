# INTERNAL TODO Registry — Relocation Index (seven-repository split)

The machine-validated `TODO(INTERNAL)` registry no longer lives in this
repository. After the seven-repository split (baseline tag
`seven-repo-split-baseline` = `bf48e15`), each canonical internal
configuration marker is owned and CI-enforced by the repository that carries
the runtime code:

| INTERNAL ID | Owning repository | Marker location |
|---|---|---|
| INTERNAL-AUD-001, CI-001, EPIC-001, IDN-001, IDN-002, JIRA-001, JOURNEY-001, POD-001, SPLUNK-001 | `Tingsum26/sdlc-workflow-service` | `src/main/java/...` (9 IDs / 18 markers) |
| INTERNAL-HOOKS-001 | `Tingsum26/sdlc-vscode-workbench` | `src/customization/bundleInstaller.ts` |

Enforcement moved with the markers:

- `sdlc-workflow-service`: `scripts/validate-internal-todos.mjs`,
  `scripts/lib/internalTodoRegistry.mjs`, `node --test`
  (`internalTodoRegistry.test.mjs`, `githubWorkflows.test.mjs`), and the
  `.github/workflows/verify-internal-todos.yml` gate. Validator reads
  `SDLC_SOURCE_ROOTS` (default `src`). Status at split: 12/12 tests green,
  9 IDs / 18 markers valid.
- `sdlc-vscode-workbench`: same toolchain. Status at split: 1 ID / 1 marker
  valid.

`INTERNAL-AUD-001` remains the only open platform-level boundary item:
aggregate persistence must be connected to the managed company MongoDB during
intranet adoption (see the M8 verification doc and the delivery manifest).
