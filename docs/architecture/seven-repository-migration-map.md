# Seven-Repository Migration Map (M0 Inventory)

- Freeze point: tag `seven-repo-split-baseline` = `bf48e15` on `agent/mvp-vertical-slice`.
- Rule (spec §21): copy → verify → switch → clean. Nothing is deleted from this
  repository until every destination repository passes its standalone acceptance
  checks.
- Scope note (spec §24): this migration relocates the EXISTING verified slice.
  Registered PARTIAL gaps (13 Agents, full MCP catalog, 8 distinct VSIX view
  models, service hierarchy depth, independent contracts release) remain open
  items tracked in the gap audit; they are not silently "completed" by moving.

## Path → destination mapping

| Source path | Destination repo | Action | Notes |
|---|---|---|---|
| `packages/contracts` | `Tingsum26/sdlc-workflow-contracts` | move | becomes independently versioned contracts package; add repo-local package.json/CI |
| `apps/workflow-service` + root `pom.xml` + `mvnw*` + `.mvn` | `Tingsum26/sdlc-workflow-service` | move | Java build re-rooted; keep fake runtime, no local/container Mongo dep |
| `apps/workflow-mcp` | `Tingsum26/sdlc-workflow-mcp` | move | consumes contracts via published dependency/reference |
| `central/*` (agents, skills, instructions, policies, templates, hooks, evals, manifests, mcp) | `Tingsum26/sdlc-copilot-customizations` | move | bundle metadata + manifests travel with it |
| `apps/vscode-extension` | `Tingsum26/sdlc-vscode-workbench` | move | UI-only extension; NO model API calls |
| `apps/web-ui` + `e2e` + `packages/ui` + `fixtures/journeys` + `playwright.config.ts` + `.demo` | `Tingsum26/sdlc-reference-demo` | move | fictional data only; browser E2E must pass WITHOUT Docker |
| `docs`, `scripts`, `.github`, root README, handoff, BOM/architecture reports | `Tingsum26/sdlc-agent-platform` (this repo) | keep→rewrite | after M7 cleanup this repo holds overview/docs/BOM/ADR/handoff only — zero runtime code |

## Shared build files disposition

- `pnpm-workspace.yaml`, root `package.json`, `tsconfig.base.json`, `eslint.config.mjs`: dissolve into per-repo equivalents during each extraction; removed in M7.
- Root `pom.xml`/`.mvn`/`mvnw.cmd`: move to workflow-service repo (only Java module).
- `pnpm-lock.yaml`: regenerated per destination repo; never copied across repos.
- `scripts/tests/*`: split by ownership (bundle tests → customizations, demo lifecycle → demo, registry validator → platform).

## Cross-repo compatibility contract

- Each new repo pins its source snapshot: `source: seven-repo-split-baseline (bf48e15)` in README.
- Platform BOM (`docs/platform-bom.yaml`) lists per-repo SemVer + commit at split time.
- Contracts repo owns schema evolution/breaking-change detection going forward.

## Acceptance per destination (minimum)

1. Standalone install/build/test green with no reference to the monorepo path layout.
2. README states public/fictional-data boundary + links back to platform BOM.
3. No credentials, no real company identifiers, no Docker dependency.
