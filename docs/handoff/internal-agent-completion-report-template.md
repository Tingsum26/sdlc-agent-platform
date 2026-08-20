# 内网 Agent 完成报告（只允许脱敏、非代码内容）

> 禁止附带代码、diff、配置全文、内部 URL/IP、Token、真实仓库/API/Jira 数据和未脱敏日志。

## 1. 报告信息

- Report ID：
- 日期：
- 公网交付版本/commit：
- 内网适配版本（仅内部别名或哈希）：
- 执行者角色（不要写姓名）：
- 环境：DEV / TEST / UAT：
- 结论：PASS / PARTIAL / FAIL / BLOCKED：

## 2. 能力与策略结果

| 能力 | 结果 | 证据摘要（非敏感） | 降级路径 |
|---|---|---|---|
| Copilot Agent Mode | | | |
| Skills | | | |
| Custom Agents/模型选择 | | | |
| Local MCP | | | |
| VSIX 安装 | | | |
| UI/UX Pro Max | | 版本/commit或“不允许安装” | |
| Company MongoDB YML/indices/connectivity | | | |
| Jira summary/attachment projection | | | |
| Pod import Skill + Workflow MCP | | | |
| GitHub Webhook/API | | | |
| Jira/Confluence API | | | |
| Jenkins状态读取 | | | |
| Graph Scanner（可选） | | | |

## 3. 完成项

| Work Item | 结果 | 验证方式 | 证据编号 |
|---|---|---|---|
| | | | |

### 3.1 证据状态转换

| Area | 公网初始状态 | 内网最终状态 | Internal Evidence ID | Observation Time | Source Type |
|---|---|---|---|---|---|
| Identity/SSO | INTERNAL_VALIDATION_REQUIRED | | | | |
| Company MongoDB | INTERNAL_VALIDATION_REQUIRED | | | | |
| Jira | SIMULATED_PASS | | | | |
| Confluence | SIMULATED_PASS | | | | |
| GHES | SIMULATED_PASS | | | | |
| Jenkins | SIMULATED_PASS | | | | |
| Splunk | SIMULATED_PASS | | | | |
| Pilot Journey | CONTRACT_PASS（仅虚构 Fixture） | | | | |

### 3.2 Internal TODO 逐项完成记录

对每一个适用的 `INTERNAL-…` ID 填一行。未执行必须说明原因；不要将代码提交当作证据。

| Internal TODO ID | Status (PASS/PARTIAL/FAIL/BLOCKED/NOT RUN) | Evidence ID（仅内网） | 实际偏差/替代方案 | 已执行或验证的回滚 | Owner 角色 |
|---|---|---|---|---|---|
| INTERNAL-XXX | | | | | |

## 4. 测试摘要

| 测试类别 | 总数 | 通过 | 失败 | 阻塞 | 跳过 | 耗时 |
|---|---:|---:|---:|---:|---:|---:|
| Unit | | | | | | |
| Contract | | | | | | |
| Integration | | | | | | |
| Workflow E2E | | | | | | |
| Manual QA E2E | | | | | | |
| Security | | | | | | |
| UX/Accessibility | | | | | | |
| Web Browser Matrix | | | | | | |
| iOS Unit/UI | | | | | | |
| Android Unit/Instrumentation/UI | | | | | | |

## 5. 验收场景结果

对每个场景填写：

- 场景 ID/通用名称：
- 结果：PASS / FAIL / BLOCKED / NOT RUN
- 覆盖的验收标准：
- 使用的环境和版本指纹（脱敏）：
- 实际结果摘要：
- 证据编号（证据保留在内网）：
- 缺陷编号（使用脱敏别名）：

## 6. 接口契约偏差

列出公网 Mock/Schema 与内网真实系统之间的差异，只描述字段类别和行为，不披露真实敏感值。

| Adapter | 偏差类别 | 影响 | 内网处理 | 是否需要公网修改 |
|---|---|---|---|---|
| | | | | |

## 7. 错误与阻断

| Error ID | 阶段 | 脱敏错误类别 | 已尝试 | 当前影响 | 建议 |
|---|---|---|---|---|---|
| | | | | | |

不要粘贴完整 Stack Trace。仅保留异常类型、HTTP 状态类别、受影响组件类型和关联证据编号。

## 8. 安全与数据边界确认

- [ ] 没有向公网上传任何公司代码或内部数据。
- [ ] VSIX/Local MCP 不持有 MongoDB 管理员凭据。
- [ ] Webhook 已验证签名、重复和重放处理。
- [ ] Jira 报告摘要/附件没有泄露源码、Prompt、客户数据或完整人员名单。
- [ ] 大附件能力不可用时已明确标记，未把完整报告塞入 Jira Comment。
- [ ] Prompt Injection 和不可信知识源已经过测试。
- [ ] 每个 SIMULATED_PASS/CONTRACT_PASS 均未被直接当成内网 PASS。
- [ ] Support Bundle 已脱敏。
- [ ] 公网仓库未包含内网配置。

## 9. UI/UX Review

- 使用 UI/UX Pro Max：YES / NO
- 使用版本/commit：
- 若未使用，原因：
- 等价 Checklist 结果：PASS / PARTIAL / FAIL
- 键盘与焦点：
- 颜色对比与非颜色状态：
- 空/错/离线/过期状态：
- 图谱可读性和表格替代：
- VS Code 明暗主题：
- 真实用户任务测试结果：
- Web 验证摘要（如适用）：
- iOS 验证摘要（如适用）：
- Android 验证摘要（如适用）：

## 10. 剩余风险

| 风险 | 概率 | 影响 | 缓解措施 | Owner角色 |
|---|---|---|---|---|
| | | | | |

## 11. 需要公网 Codex Review 的问题

只提交可以在不知道内网代码的情况下回答的架构、契约、测试和故障诊断问题：

1. （填写问题；没有则写“无”）

## 12. 内网 Agent 声明

- [ ] 报告内容均已脱敏。
- [ ] 所有 PASS 均有内网证据支持。
- [ ] 未将“生成了代码”当作“测试通过”。
- [ ] 已明确区分完成、部分完成、失败和未执行。
- [ ] 报告不包含代码或可还原的内部信息。
