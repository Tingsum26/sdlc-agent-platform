# Internal Agent Completion Report — {reportId}

> Redacted, non-code content only: no code, diffs, full configs, internal URLs/IPs, tokens, real repository/API/Jira data, or unmasked logs.

## 1. Report info

- Report ID: {reportId}
- Date: {date}
- Public delivery version / commit: {publicVersion}
- Internal adaptation version (internal alias or hash only): {internalVersion}
- Executor role (no names): {executorRole}
- Environment: {DEV | TEST | UAT}
- Conclusion: {PASS | PARTIAL | FAIL | BLOCKED}

## 2. Capability and policy results

| Capability | Result | Evidence summary (non-sensitive) | Degraded path |
|---|---|---|---|
| Copilot Agent Mode | | | |
| Skills | | | |
| Custom Agents / model selection | | | |
| Local MCP | | | |
| VSIX install | | | |
| UI/UX Pro Max | | version/commit or "not allowed to install" | |
| Company MongoDB YML/indices/connectivity | | | |
| Jira summary/attachment projection | | | |
| Pod import Skill + Workflow MCP | | | |
| GitHub Webhook/API | | | |
| Jira/Confluence API | | | |
| Jenkins status read | | | |
| Graph Scanner (optional) | | | |

## 3. Completed items

| Work Item | Result | Verification | Evidence ID |
|---|---|---|---|
| | | | |

### 3.1 Evidence state transitions

| Area | Public initial state | Internal final state | Internal Evidence ID | Observation Time | Source Type |
|---|---|---|---|---|---|
| Identity/SSO | INTERNAL_VALIDATION_REQUIRED | | | | |
| Company MongoDB | INTERNAL_VALIDATION_REQUIRED | | | | |
| Jira | SIMULATED_PASS | | | | |
| Confluence | SIMULATED_PASS | | | | |
| GHES | SIMULATED_PASS | | | | |
| Jenkins | SIMULATED_PASS | | | | |
| Splunk | SIMULATED_PASS | | | | |
| Pilot Journey | CONTRACT_PASS (fictitious fixture only) | | | | |

## 4. Test summary

| Test category | Total | Passed | Failed | Blocked | Skipped | Duration |
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

## 5. Acceptance scenario results

- Scenario ID / common name: {scenarioId}
- Result: {PASS | FAIL | BLOCKED | NOT_RUN}
- Acceptance criteria covered: {acceptanceCriteria}
- Environment and version fingerprint (redacted): {fingerprint}
- Actual result summary: {actualResult}
- Evidence ID (retained internally): {evidenceId}
- Defect ID (redacted alias): {defectAlias}

## 6. Interface contract deviations

| Adapter | Deviation category | Impact | Internal handling | Public change needed |
|---|---|---|---|---|
| | | | | |

## 7. Errors and blockers

| Error ID | Stage | Redacted error category | Attempted | Current impact | Suggestion |
|---|---|---|---|---|---|
| | | | | | |

> No full stack traces: exception type, HTTP status category, affected component type, and evidence ID only.

## 8. Security and data boundary confirmation

- [ ] No company code or internal data uploaded to the public repository.
- [ ] VSIX/Local MCP holds no MongoDB administrator credentials.
- [ ] Webhook signature, duplicate, and replay handling verified.
- [ ] Jira summary/attachment leaks no source, prompt, customer data, or full personnel list.
- [ ] Large-attachment unavailability marked; no full report stuffed into a Jira comment.
- [ ] Prompt injection and untrusted knowledge sources tested.
- [ ] No SIMULATED_PASS/CONTRACT_PASS treated as internal PASS.
- [ ] Support Bundle redacted.
- [ ] Public repository contains no internal configuration.

## 9. UI/UX Review

- UI/UX Pro Max used: {YES | NO}
- Version/commit: {version}
- If not used, reason: {reason}
- Equivalent checklist result: {PASS | PARTIAL | FAIL}
- Keyboard and focus: {result}
- Color contrast and non-color state: {result}
- Empty/error/offline/expired states: {result}
- Graph readability and table alternative: {result}
- VS Code light/dark themes: {result}
- Real user task test result: {result}
- Web verification summary (if applicable): {summary}
- iOS verification summary (if applicable): {summary}
- Android verification summary (if applicable): {summary}

## 10. Residual risks

| Risk | Probability | Impact | Mitigation | Owner role |
|---|---|---|---|---|
| | | | | |

## 11. Questions for public Codex review

1. {question; "none" if empty}

## 12. Internal agent declaration

- [ ] Report content is redacted.
- [ ] Every PASS has internal evidence.
- [ ] "Generated code" is not reported as "tests passed".
- [ ] Completed, partial, failed, and not-run are distinguished.
- [ ] Report contains no code or reversible internal information.
