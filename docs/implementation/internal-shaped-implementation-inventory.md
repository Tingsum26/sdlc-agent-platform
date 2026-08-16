# Internal-shaped implementation inventory

This inventory describes what the public branch actually implements. `SIMULATED_PASS` is deterministic fake evidence, `CONTRACT_PASS` is structural validation, and neither is company-environment proof.

## Runtime components

| Component | Implemented responsibility | AI boundary |
|---|---|---|
| Workflow Service | state, audit, artifacts, identity binding, Pod rosters, assignment, adapters, Journey analysis, HTML | no model client |
| Workflow MCP | 12 bounded tools, validation, confirmation, cancellation, safe errors | invokes Workflow Service only |
| VSIX | nine views, polling/manual refresh, readiness evidence, reports, approvals, MCP onboarding, diagnostics | no Language Model API |
| Web UI | fictional demonstrator and human-readable report preview | no model client |
| Central customization | Agents, Skills, Instructions, policies, schemas, evals and bundle manifest | Copilot is started by a person |

## Workflow Service additions

- Enterprise transport: provider allow-list, timeout, cancellation, response-size limit, retry/error classification, credential indirection, safe exception surface, deterministic fake transport.
- Adapters: Jira read/comment shape, Confluence page read, GHES checks, Jenkins build status, Splunk diagnostic query shape.
- Identity: administrator employee binding supports a Scrum Master without GitHub access. Public fixture is `EMP-100` / `PRINCIPAL-EMP-100`.
- Pod and assignment: atomic roster validation/import, optimistic revision, audit attribution and deterministic role assignment.
- Mongo profile: six Spring Data documents/repositories plus reviewed index manifest; no local database or container.
- Journey: API/Web/iOS/Android repository manifest, screen-to-API edge, request/response schema refs, common header, provenance, compatibility, web/API-first/native-later policy, feature flag and E2E owner checks.
- HTML: standalone script-free report with escaped content, textual evidence status and accessibility labels.

## REST surface

| Method/path | Effect |
|---|---|
| `GET /api/v1/internal-readiness/identity` | read bound enterprise principal |
| `POST /api/v1/internal-readiness/pods/validate` | validate without persistence |
| `POST /api/v1/internal-readiness/pods/import` | persist with expected revision and audit actor |
| `GET /api/v1/internal-readiness/pods/{journeyId}` | read roster |
| `POST /api/v1/internal-readiness/assignments` | assign a Ticket to a Pod role/principal |
| `GET /api/v1/internal-readiness/assignments/{ticketId}` | read assignment |
| `GET /api/v1/internal-readiness/integrations` | read five evidence-labelled diagnostics |
| `GET /api/v1/internal-readiness/next-validation` | obtain next internal proof action |
| `POST /api/v1/journeys/validate` | validate bounded v1 input |
| `POST /api/v1/journeys/analyze` | return ordered gaps and relationship coverage |
| `POST /api/v1/journeys/report` | return safe standalone HTML |

## Contracts and persistence

New JSON Schemas are `evidence-status-v1`, `enterprise-principal-v1`, `pod-roster-v1`, `integration-diagnostic-v1`, and `journey-manifest-v1`. Mongo collections are workflow tasks, audit events, artifacts, webhook deliveries, Pod rosters and task assignments. Configuration is in `application-mongodb.example.yml`; indexes are in `mongo-indexes-v1.json`.

## Local MCP tools

The existing six task tools are joined by `workflow_get_identity`, `workflow_validate_pod_roster`, `workflow_import_pod_roster`, `workflow_get_integration_diagnostics`, `workflow_analyze_journey`, and `workflow_get_next_internal_validation`. Import accepts only literal `confirmed: true`; input arrays are bounded before network access.

## UI implementation

VSIX Diagnostics displays identity, provider, evidence text, source, observation time and next internal action. It polls through Workflow Service with the same foreground/background rules as task state and supports manual/focus refresh. Journey reports are selected from a local JSON manifest and opened in a script-disabled panel.

The public Web UI contains an explicitly fictional `EPIC-DEMO-1` Account Opening scenario. Its semantic table has text plus icon status, keyboard focus, responsive overflow, 44 px narrow-screen controls, reduced-motion handling and a sandboxed report iframe.

## Deliberately not implemented publicly

Company credentials/endpoints, real Mongo connectivity, SSO, Teambook, GHES release signing, real Jira/Confluence/GHES/Jenkins/Splunk proof, repository graph extraction, real Journey manifests, production authorization, native app execution, Figma connection and LLM Wiki. These require the internal completion report.
