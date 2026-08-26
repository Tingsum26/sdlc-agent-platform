# Public MVP walkthrough

This demo uses fictional data, in-memory Fake repositories, local processes, and no Docker, database, object store, cloud agent, or company connection.

## Automated proof

```powershell
pnpm install --frozen-lockfile
pnpm exec playwright install chromium
pnpm e2e:public-mvp
```

The browser test starts Workflow Service and Web UI, creates `DEMO-123`, calls the six-tool MCP protocol in process, claims the task, submits and confirms a structured report, records human approval, attaches mock CI and traceable manual E2E evidence, verifies `COMPLETED`, reads seven audit events, and verifies the rendered HTML artifact.

## Manual exploration

```powershell
.\scripts\start-demo.ps1
```

Open `http://127.0.0.1:4173`, select **Create DEMO-123**, and inspect its text/icon status. Configure `.vscode/mcp.json` from `.vscode/mcp.example.json`, build the MCP, then use `/start-ticket DEMO-123` or `/resume-workflow <task-id>` from local Copilot Chat. Stop with:

```powershell
.\scripts\stop-demo.ps1
```

The public demo header is accepted only over loopback. It is not an enterprise authentication implementation.
