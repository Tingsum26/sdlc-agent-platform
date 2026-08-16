# PUBLIC DELIVERY MANIFEST

## 1. 交付信息

- Release/Commit：
- 日期：
- 设计文档版本：
- 实施计划版本：
- 兼容 Schema 版本：

## 2. 公网已完成

| Component/Work Item | 状态 | 文件/制品 | 公网验证证据 |
|---|---|---|---|
| | | | |

状态只允许：`COMPLETE`、`PARTIAL`、`NOT_STARTED`、`NOT_APPLICABLE`。

## 3. 公网已执行验证

| 命令/场景 | 结果 | 数量/摘要 | 限制 |
|---|---|---|---|
| | | | |

## 4. Mock 与假设

| 外部系统 | 公网 Mock 行为 | 假设 | 内网必须验证 |
|---|---|---|---|
| GitHub Enterprise Server | | | |
| Jira | | | |
| Confluence | | | |
| Jenkins | | | |
| Company MongoDB configuration | | | |
| Jira report summary/attachment | | | |
| Pod import Workflow MCP | | | |
| Copilot/VS Code | | | |

## 5. 内网 Agent 必须完成

| Internal Work Item | 前置条件 | 验收标准 | 报告证据要求 |
|---|---|---|---|
| | | | |

## 6. 不可由公网验证

- 真实内网认证、RBAC 和网络；
- 真实 GitHub/Jira/Confluence/Jenkins 版本与行为；
- 内网 Copilot 企业策略和可用模型；
- 真实仓库、Journey 和 API 关系；
- 内网 MongoDB 的性能、备份、安全与保留；
- Jira Attachment 的权限、大小、保留和失败行为；
- 公司安全、许可证和生产变更审批。

## 7. 已知限制与风险

| ID | 限制/风险 | 影响 | 公网缓解 | 内网动作 |
|---|---|---|---|---|
| | | | | |

## 8. 内网执行入口

- 安装文档：
- 配置 Schema：
- 示例环境（Fake Adapter；不启动本地数据库）：
- 验证命令/测试场景：
- 回滚方式：
- 完成报告模板：`docs/handoff/internal-agent-completion-report-template.md`

## 9. 数据边界声明

- [ ] 交付不含公司代码、域名、Token、证书或业务数据。
- [ ] 示例只使用虚构名称与数据。
- [ ] 日志和测试制品已检查敏感信息。
- [ ] 所有第三方依赖均列出版本和许可证。
