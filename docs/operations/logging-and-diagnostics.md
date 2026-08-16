# Logging and diagnostics contract

## Common event fields

Every component emits one JSON object per event with `timestamp`, `level`, `component`, `event`, and, where applicable, `correlationId`, `taskId`, `actorId`, `status`, `durationMs`, and safe error category. Never log prompts, source, request/response bodies, report sections, tokens, passwords, cookies, authorization headers, certificates, customer data, or full stack traces in user-facing bundles.

| Component | MVP destination | Internal target | Debug entry |
|---|---|---|---|
| Workflow Service/API | stdout + service log | Existing API Splunk pipeline | correlation ID and `http_request_completed` |
| Workflow MCP | stderr only; stdout remains MCP protocol | local support bundle or approved forwarder | tool name, status class, correlation ID |
| VSIX | `Local Copilot SDLC` Output Channel | optional redacted telemetry API to Splunk after approval | Diagnostics view and correlation ID |
| Web demo | browser console only for development errors | none | Playwright trace on failure |

Workflow audit events are business evidence stored through the Workflow Service repository; logs are operational evidence and are not authoritative workflow state. The internal team must define retention, Splunk index/source type, PII handling, rate limits, sampling, clock synchronization, alerting, and support-bundle redaction before production.
