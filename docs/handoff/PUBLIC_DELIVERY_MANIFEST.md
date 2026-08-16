# PUBLIC DELIVERY MANIFEST

## 1. 交付信息

- Release/Commit：`agent/mvp-vertical-slice`（以 Draft PR head commit 为准）
- 日期：2026-08-16
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
| Workflow MCP | COMPLETE | `apps/workflow-mcp` | 12-tool discovery, bounds, confirmation, cancellation, correlation, redaction |
| Central Agents/Skills/Instructions | COMPLETE | `.github`, `skills` | customization and bundle contract tests |
| Policies/Schemas/MCP catalog/Evals | COMPLETE | `policies`, `packages/contracts`, `mcp`, `evals` | strict schema/config tests |
| VSIX workbench | COMPLETE | `apps/vscode-extension` | polling, ETag, boundary, bundle validation, typecheck, VSIX package |
| Customization bundle local install/rollback | COMPLETE | VSIX + `manifests` | path-boundary tests; explicit human activation |
| GHES release signature/distribution | NOT_STARTED | internal-only | requires company GHES/policy |
| Shared accessible HTML UI | COMPLETE | `packages/ui` | keyboard/status/approval/manual-E2E tests |
| Public Web demo | COMPLETE | `apps/web-ui` | component test + production build |
| Full public browser vertical slice | COMPLETE | `e2e/public-mvp.spec.ts` | Playwright Chromium PASS |
| Logging/diagnostics contract | COMPLETE | service/MCP/VSIX + docs | redaction tests and structured event implementations |
| LLM Wiki | NOT_STARTED | extension backlog | deliberately excluded from MVP |
| Cross-repository Journey manifest/analyzer | COMPLETE | schema, Java analyzer, fixture and HTML | fictional contract/browser tests; real graph remains internal |

状态只使用：`COMPLETE`、`PARTIAL`、`NOT_STARTED`、`NOT_APPLICABLE`。

## 3. 公网已执行验证

| 命令/场景 | 结果 | 数量/摘要 | 限制 |
|---|---|---|---|
| `.\mvnw.cmd verify` | PASS | 37 unit + 12 integration = 49 tests | Fake runtime and mocked Mongo operations; no real Mongo connectivity |
| `pnpm test` | PASS | 49 tests: Contracts 22, MCP 8, UI 8, VSIX 9, Web 2 | deterministic public adapters only |
| `pnpm build` | PASS | Contracts, MCP, UI, VSIX and production Web bundle | public build only |
| `pnpm e2e:public-mvp` | PASS | one full seven-audit-event browser scenario | fictional loopback demo |
| `pnpm exec playwright test e2e/internal-shaped-simulation.spec.ts` | PASS | identity, Pod, assignment, five diagnostics, Journey and HTML | fictional loopback demo |
| `.\scripts\tests\stop-demo.test.ps1` | PASS | UTC process identity regression | Windows PowerShell process behavior |
| `pnpm --filter sdlc-workbench package` | PASS | `.vsix` generated | not signed/published internally |
| `pnpm audit --audit-level low` | PASS | no known npm vulnerabilities | snapshot of registry advisory data at verification time |
| start → health → stop → port release | PASS | Workflow `UP`, Web `200`, ports 8080/4173 released | Fake profile only |
| no-prohibited-infrastructure/secret scan | PASS | no Docker/local DB/object store/cloud-agent dependency | not a company security scan |

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
