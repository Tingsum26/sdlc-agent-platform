# PUBLIC DELIVERY MANIFEST

## 1. 交付信息

- Release/Commit：`agent/mvp-vertical-slice`（以 Draft PR head commit 为准）
- 日期：2026-08-21（M8 公网验证）；2026-08-22 推送前全矩阵复验
- 设计文档版本：v2
- 实施计划版本：2026-08-14, reviewed 2026-08-16
- 兼容 Schema 版本：1.0

## 2. 公网已完成

| Component/Work Item | 状态 | 文件/制品 | 公网验证证据 |
|---|---|---|---|
| Pre-implementation review | COMPLETE | `docs/reviews` | PROCEED decision; version/threat/constraint review |
| Workflow state, leases, audit | COMPLETE | `apps/workflow-service` | Java unit/integration tests |
| REST/Webhook/security errors | COMPLETE | Workflow Service API | HMAC, duplicate, auth, stale-version tests |
| Structured reports/approval/Jira projection port | COMPLETE | Workflow Service artifact domain | hash, escaping, immutability, retry tests |
| Company Mongo runtime shape | COMPLETE | documents, repositories, example YML + index contract | mapping/CAS/configuration tests; connectivity is internal |
| Enterprise identity/Pod/assignment | COMPLETE | Workflow Service domain/API | deterministic unit/integration tests |
| Enterprise adapter shape | COMPLETE | Jira/Confluence/GHES/Jenkins/Splunk adapters | deterministic transport and safe-error tests |
| Workflow MCP | PARTIAL | `apps/workflow-mcp` | 23 registered deterministic tools pass; the complete approved onboarding/QA/Scrum/adapter catalog is still larger than this runnable slice |
| Central Agents/Skills/Instructions | PARTIAL | `central/{agents,skills,instructions}` | Actual inventory is 13 Agents, 33 Skills, and 19 Instructions; completeness remains PARTIAL against the seven-repository target and internal validation |
| Policies/Schemas/MCP catalog/Evals | PARTIAL | `policies`, `packages/contracts`, `mcp`, `evals` | slice contracts pass; independent contracts and the complete policy/eval catalog are missing |
| VSIX workbench | PARTIAL | `apps/vscode-extension` | Eight independently registered views exist (Repo Task is nested under Ticket); internal authentication, organization distribution and the full target action catalog remain pending |
| Customization bundle local install/rollback | COMPLETE | VSIX + `manifests` | path-boundary tests; explicit human activation |
| GHES release signature/distribution | NOT_STARTED | internal-only | requires company GHES/policy |
| Shared accessible HTML UI | COMPLETE | `packages/ui` | keyboard/status/approval/manual-E2E tests |
| Public Web demo | COMPLETE | `apps/web-ui` | component test + production build |
| Full public browser vertical slice | COMPLETE | `e2e/public-mvp.spec.ts` | Playwright Chromium PASS |
| Logging/diagnostics contract | COMPLETE | service/MCP/VSIX + docs | redaction tests and structured event implementations |
| LLM Wiki | NOT_STARTED | extension backlog | deliberately excluded from MVP |
| Cross-repository Journey manifest/analyzer | PARTIAL | schema, Java analyzer, fixture and HTML | fictional analyzer passes; complete onboarding, graph freshness and impact workflow remain missing |
| M8 internal TODO registry and CI gate | COMPLETE | `docs/handoff/internal-todo-registry.json`, validator, GitHub Actions workflow | 10 IDs / 19 canonical source marker paths; JSON/Markdown/source parity tests pass |
| M2–M4 aggregate Mongo persistence (`INTERNAL-AUD-001`) | NOT_STARTED | internal-only implementation | public fake runtime remains intentionally in-memory; managed Mongo validation is pending |
| Approved seven-repository split | NOT_STARTED | target delivery structure | public run-first monorepo is verified; the seven independent repositories are pending |

状态只使用：`COMPLETE`、`PARTIAL`、`NOT_STARTED`、`NOT_APPLICABLE`。

### 2.1 实际可执行清单（由源码重新核对）

- Workflow MCP（23）：`workflow_list_my_tasks`, `workflow_get_task_context`, `workflow_claim_task`, `workflow_submit_artifact`, `workflow_request_approval`, `workflow_complete_task`, `workflow_get_identity`, `workflow_validate_pod_roster`, `workflow_import_pod_roster`, `workflow_get_integration_diagnostics`, `workflow_analyze_journey`, `workflow_get_next_internal_validation`, `workflow_epic_create`, `workflow_epic_activate`, `workflow_epic_attach_ticket`, `workflow_ticket_advance`, `workflow_ticket_add_repo_task`, `advance_repo_task`, `workflow_epic_add_dependency`, `workflow_epic_create_change_request`, `workflow_epic_approve_change_request`, `workflow_task_skip`, `workflow_epic_resume`。
- Central Agents（13）：Accessibility QA、Android Implementer、Code Context Analyst、Delivery Coordinator、Epic Delivery Analyst、iOS Implementer、Java Implementer、Planner、PR Reviewer、Requirement Analyst、Solution Architect、Test Designer、Web Implementer。
- VSIX Views（8）：My Work、Scrum Master、Epic、Ticket（内嵌 Repo Task）、Identity / Pod、Customization Center、MCP Center、Diagnostics。

上述数量说明当前仓库真实存在且可测试的表面，并不把目标平台误报为完成：Local MCP 尚未覆盖全部中央 Skill 的专用工具，VSIX 尚未接入公司认证/发布策略，中央配置也尚未完成内网模型、权限和产品版本验证。

> 2026-08-16 re-audit: `COMPLETE` means complete only where the row describes a bounded deliverable. It must not be read as completion of the target platform. The authoritative target and gap inventory are `docs/superpowers/specs/2026-08-16-seven-repository-platform-design.md` and `docs/reviews/2026-08-16-seven-repository-gap-audit.md`.

## 3. 公网已执行验证

以下条目均在 2026-08-21 M8 验证中重新执行；并于 2026-08-22（推送前，覆盖六个安全/原子性收尾提交之后）全矩阵复验。旧里程碑统计不再作为当前交付结论。

| 命令/场景 | 结果 | 数量/摘要 | 限制 |
|---|---|---|---|
| `./mvnw.cmd -q verify` | PASS | 2026-08-21: 163 tests/41 reports; 2026-08-22: 175 tests in 42 Surefire reports; 0 failures, 0 errors, 0 skipped | Fake runtime and mocked Mongo operations; no real Mongo connectivity |
| `pnpm install --frozen-lockfile` + `pnpm test` | PASS | 2026-08-22: 123 tests: Contracts 35, Workflow MCP 13, VSIX 61, shared UI 8, Web UI 6（08-21 为 119） | deterministic public adapters only |
| `pnpm build` + `pnpm lint` | PASS | all five runnable workspaces built; no failing lint script | public build only |
| `pnpm e2e:public-mvp`, `e2e:m1`, `e2e:m2`, `e2e:m3`, `e2e:m4`, `e2e:m7` | PASS | each suite 1/1, launched separately with ports 8080/4173 clean; public-mvp had one transient batched-run failure, standalone rerun green (flake note in M8 doc) | fictional loopback demo |
| `powershell -File scripts/tests/build-bundle.test.ps1` + `bundle-lifecycle.test.ps1` | PASS | central bundle build and install/rollback lifecycle | no company release trust |
| `powershell -File scripts/tests/stop-demo.test.ps1` | PASS | Windows PID-reuse, process-tree, and discovery-failure regression cases | Windows PowerShell process behavior |
| `pnpm --filter sdlc-workbench package` | PASS | typecheck/build/package; 2026-08-22: 7-file 18.78-KB `.vsix`（08-21 为 6-file 18.01-KB） | not signed/published internally |
| `pnpm audit --audit-level low` | PASS | no known vulnerabilities | snapshot of registry advisory data at verification time |
| start → health → stop → port release | PASS | Workflow `UP`, Web `200`, ports 8080/4173 released | Fake profile only |
| TODO registry + static scans | PASS | registry tests 11/11; 10 IDs/19 canonical paths; marker scan 19; credential scan 0 | exact commands and exclusions in `docs/verification/m8-milestone-2026-08-20.md` |

## 4. Mock 与假设

| 外部系统 | 公网 Mock 行为 | 假设 | 内网必须验证 |
|---|---|---|---|
| GitHub Enterprise Server | fictional SCM event + signed webhook | API/webhook available | auth, versions, permissions, replay, checks |
| Jira | adapter port and pending projection status | API token/delegation available | comments, attachments, limits, sanitization |
| Confluence | catalog entry only | authorized read API available | ACL preservation, provenance, injection |
| Jenkins | read-only mock CI state | existing Groovy pipeline emits status | webhook/check mapping, no model/scanner assumption |
| Company MongoDB | YML placeholders + index manifest | managed Mongo available; GridFS optional | drivers, TLS, indices, backup, retention, performance |
| Jira report summary/attachment | concise projection port | Jira is non-authoritative | size/ACL/retention/failure fallback |
| Pod import Workflow MCP | central Skill/templates/evals | admin roster source available | employee mapping, dates, duplicates, Teambook future |
| Copilot/VS Code | static customizations and local MCP | Agent/Skill/MCP features enabled | policy, actual models, install/discovery, SM path |

## 5. 内网 Agent 必须完成

| Internal Work Item | 前置条件 | 验收标准 | 报告证据要求 |
|---|---|---|---|
| Enterprise identity and audit mapping | GHES/SSO decision | developer and non-GitHub SM paths work | role-only result + evidence IDs |
| Mongo connection validation | managed Mongo config | restart/resume, optimistic lock, indices, backup tested | counts, latency bands, restore result |
| Jira/Confluence/GHES/Jenkins/Splunk adapter connection | delegated credentials | context, comments, PR/check/build/search reads work | abstract contract deviation table |
| Signed bundle GHES release | release policy/certificate | hash/signature, compatibility, rollback | install/rollback scenario IDs |
| Account Opening Journey pilot | approved repo/Journey list | page→API/payload/header/native compatibility map | coverage summary, no repo/API names publicly |
| Splunk operations | approved source types | correlation search, alerts, redacted support bundle | event counts and evidence IDs |
| Security/accessibility/QA | internal environments | threat, WCAG, manual E2E, browser/native evidence | completed internal report sections |

## 6. 不可由公网验证

真实内网认证/RBAC/网络、GHES/Jira/Confluence/Jenkins versions、Copilot enterprise policy/models、真实 repository/Journey/API relationships、Mongo performance/backup/security/retention、Jira attachment policy、Splunk routing、company security/license/production approvals。

## 7. 已知限制与风险

| ID | 限制/风险 | 影响 | 公网缓解 | 内网动作 |
|---|---|---|---|---|
| R-01 | Fake runtime resets on restart | public state is non-durable | deterministic E2E | implement Mongo ports |
| R-02 | MVP demo header is not enterprise auth | no production identity | loopback enforcement | GHES + employee binding/SSO |
| R-03 | Reviewer model names may be unavailable | agent may fall back/ignore | ordered Copilot model list | validate and pin approved model |
| R-04 | Bundle folder is locally selected | no organization release trust | schema/path validation + rollback | signed GHES release and hashes |
| R-05 | Real code graph unavailable | Journey relationships unknown | explicit onboarding blocker | pilot scanner/Understand Anything locally |
| R-06 | Large evidence store absent | screenshots/video not stored | evidence reference/waiver and Jira concise summary | approve provider later; S3 is post-MVP |
| R-07 | LLM Wiki excluded | no synthesized wiki memory | source-first onboarding + classified knowledge plan | separately approve provenance/ACL experiment |
| R-08 | `SIMULATED_PASS` provenance is session-scoped | no real QA/release/company proof or cross-restart provenance | visible simulated classification and fake-runtime tests | obtain internal evidence IDs from actual systems and retain them in managed Mongo |

## 8. 内网执行入口

- 安装文档：`README.md`, `docs/demo/public-mvp-walkthrough.md`
- 配置 Schema：`packages/contracts/schemas`, `policies`, `mcp/catalog.json`
- Mongo 示例：`apps/workflow-service/src/main/resources/application-mongodb.example.yml`
- 验证命令：README **Verify** section
- 回滚：uninstall VSIX; use **Roll Back Customization Bundle**; restore previous internal service release
- 移交：`docs/handoff/INTERNAL_AGENT_HANDOFF.md`
- 完成报告：`docs/handoff/internal-agent-completion-report-template.md`

## 9. 数据边界声明

- [x] 不含公司代码、域名、Token、证书或业务数据。
- [x] 示例只使用虚构名称与数据。
- [x] 日志、错误和测试制品使用脱敏/结构化契约。
- [x] 依赖版本记录在 Maven/npm lockfiles；内网仍须完成许可证审批。

## 5. 七仓终态（2026-08-22 M0–M7 完成）

运行时代码已按 `seven-repo-split-baseline`（bf48e15）拆分至六个独立公开仓库，全部独立验证后推送：
contracts 38457cf · service f69352a（123 单测+57 IT）· mcp f0bfa80（13）· customizations a029cd2（20 守护测试）· vscode-workbench 9f40c53（61+VSIX）· reference-demo 68862aa（14+build）。本仓保留 overview/docs/BOM/handoff，无运行时代码；内部 TODO registry 已随标记迁移（见 docs/handoff/internal-todo-relocation.md）。PARTIAL 缺口（13 Agents、完整 MCP 目录、8 视图模型等）与 INTERNAL-AUD-001 保持为登记在案的后续工作。
