# 公网实现到内网落地移交说明

## 1. 目的

公网 Codex 负责完成不依赖公司内网真实环境的通用代码、协议、文档、模拟适配器和测试。内网 GitHub Copilot Agent 负责真实系统连接、企业配置、权限验证和环境测试。内网禁止向公网上传公司代码，因此双方只通过脱敏的非代码报告协作。

## 2. 公网 Codex 负责完成

- Workflow Service 通用状态机、REST API、幂等、任务 lease、审计接口；
- MongoDB 数据模型、Java/Spring `application-mongodb.example.yml`、环境变量契约、Index Manifest 和 Fake Repository 单元测试；
- 结构化 Mongo Artifact 抽象、按需 HTML 渲染和 Jira 摘要/可选附件投影；
- Local MCP 通用工具协议与 Mock Workflow Adapter；
- VSIX HTML 工作台、任务/审批/报告 UI 和 MCP 诊断 UI；
- Web UI 共用组件；
- 中央 Agents、Skills（含 `importing-pod-members`）、Instructions、Hooks、MCP、Schema、Policy、Eval 和 Bundle Manifest；
- GitHub/Jira/Confluence/Jenkins 的接口契约及 Mock Server；
- Repo/Journey Onboarding、测试报告、手工 E2E、Support Bundle 模板；
- 单元、契约、组件及可在公网模拟环境执行的 E2E 测试；
- 不依赖本地数据库或容器的 Fake Adapter 演示环境；
- 安装、配置、升级、回滚和内网适配指南；
- 不包含公司域名、证书、Token、真实业务字段、仓库内容或人员信息。

每次交付必须附一份 `PUBLIC_DELIVERY_MANIFEST.md`，列出完成项、版本、验证命令、已知限制、Mock 假设和需要内网完成的接口。

## 3. 内网 Agent 必须完成

以下任务只有内网环境可知，不能由公网 Codex 猜测：

1. 确认真实 GitHub Enterprise Server 版本、API Base URL、GitHub App 权限、Webhook 路由、Branch Protection 和 Checks 支持。
2. 确认 Jira/Confluence 版本、认证方式、API 字段映射、分页、限流和权限继承。
3. 确认 Jenkins 回写的真实 Check/Status 名称、报告 URL、PR 与默认分支事件行为；不得擅自修改现有 Jenkins 环境。
4. 建立 Workflow Service 的内网部署配置、TLS、代理、CA、DNS、身份认证、RBAC 和网络白名单。
5. 配置 Workflow Service 的真实 MongoDB YML/Secret，建立数据库、索引、账号、备份、恢复、保留和容量策略，并在批准的非生产环境运行集成测试。
6. 验证 Jira Comment 摘要和 HTML/PDF Attachment 权限、大小、保留与失败重试；不可用时确认 `LARGE_ARTIFACT_STORAGE_UNAVAILABLE` 降级。GridFS/S3 仅在公司后期批准后评估。
7. 配置并验证 GitHub/Jira/Jenkins Webhook Secret、签名、防重放、重复/乱序/丢失补偿。
8. 安装/配置 VSIX、Local MCP、Skills、Custom Agents 和企业 Copilot 策略。
9. 用 3–5 个获批仓库执行 Repo Onboarding 和开户 Journey 试点。
10. 验证跨仓权限：用户只能通过平台读取其本来有权访问的数据。
11. 运行真实 CI、审批、QA 手工 E2E 和报告链接闭环。
12. 验证 Agent Mode、MCP 或 Graph Scanner 不可用时的降级路径。
13. 完成安全扫描、依赖许可证审核、漏洞处置和生产变更审批。
14. 确认 UI/UX Pro Max Skill 是否允许安装；允许时记录版本并用于 VSIX 设计评审，不允许时执行等价 UX Checklist。
15. 对试点中的业务 Web、iOS、Android 仓库分别验证构建工具、设备/模拟器、签名配置、企业证书、代理、API 环境和 UI 自动化能力；报告不得包含签名材料或内部应用信息。
16. 使用 `importing-pod-members` Skill 和 Workflow MCP 对虚构/获批的 Pod CSV 执行 Dry Run、冲突停止、明确确认、幂等 Apply、未 Onboard 人员和 Import Report 场景。

## 4. 严禁回传公网的内容

- 任何源代码、diff、patch、完整配置文件或内部脚本；
- 公司域名、IP、证书、Token、Cookie、用户名、邮箱和团队名单；
- Jira/Confluence 正文、真实 Ticket、客户数据和生产数据；
- 内部仓库名、服务名、API Path、数据库名和架构拓扑；
- 未脱敏的日志、截图、Trace、Stack Trace 或 Jenkins Console；
- 能推断公司安全控制、网络边界或漏洞利用路径的信息。

若无法安全描述，使用通用别名，例如 `REPO_A`、`SERVICE_B`、`INTERNAL_HOST_1` 和错误码摘要。

## 5. 内网 Agent 工作规则

1. 先阅读公网交付的设计、实施计划、`PUBLIC_DELIVERY_MANIFEST.md` 和本文件。
2. 只修改内网仓库；不得尝试向公网 Push。
3. 为每项内网配置保留内部证据，但只向公网输出脱敏报告。
4. 不得声称“通过”而不执行实际命令或场景。
5. 报告测试名称、结论、数量、耗时和脱敏错误类别，不粘贴源码或完整日志。
6. 遇到阻断时，说明限制、已尝试步骤、最小复现类别和需要公网 Codex 回答的设计问题。
7. 完成后必须使用 `internal-agent-completion-report-template.md` 输出报告。

## 6. 公网 Codex Review 流程

```text
公网实现与Manifest
  → 内网拉取
  → 内网Agent适配和验证
  → 输出脱敏非代码报告
  → 用户把报告发给公网Codex
  → Codex Review结论：ACCEPT / QUESTIONS / CHANGES_REQUIRED
  → Codex提供设计建议或公网通用修复
  → 内网Agent再次验证并更新报告
```

公网 Codex 只能根据报告审查设计一致性、测试充分性、风险和错误分类，不能声称独立验证了内网代码或环境。
