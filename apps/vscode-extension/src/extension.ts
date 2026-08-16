import { existsSync } from "node:fs";
import { join } from "node:path";
import * as vscode from "vscode";
import { WorkflowClient, type WorkflowTask } from "./api/workflowClient.js";
import { checkMcpHealth } from "./diagnostics/mcpHealth.js";
import { ExtensionLogger } from "./logging/logger.js";
import { TaskPoller } from "./polling/taskPoller.js";
import { TaskTreeProvider } from "./views/taskTreeProvider.js";
import { WorkflowStatusBar } from "./views/statusBar.js";
import { openApprovalPanel } from "./webview/approvalPanel.js";
import { escapeHtml, shell } from "./webview/html.js";
import { openReportPanel } from "./webview/reportPanel.js";

const viewIds = ["sdlc.developer", "sdlc.scrumMaster", "sdlc.myWork", "sdlc.epic", "sdlc.ticket",
  "sdlc.repoTask", "sdlc.customization", "sdlc.mcpCenter", "sdlc.diagnostics"];

export function activate(context: vscode.ExtensionContext): void {
  const output = vscode.window.createOutputChannel("Local Copilot SDLC");
  const logger = new ExtensionLogger(output);
  const config = () => vscode.workspace.getConfiguration("sdlc");
  const client = () => new WorkflowClient(config().get<string>("workflowServiceUrl", "http://127.0.0.1:8080"),
    fetch, config().get<string>("demoActorId") || undefined);
  const providers = viewIds.map((id) => new TaskTreeProvider(id));
  const status = new WorkflowStatusBar();
  let tasks: WorkflowTask[] = [];

  for (let index = 0; index < viewIds.length; index += 1) {
    context.subscriptions.push(vscode.window.registerTreeDataProvider(viewIds[index]!, providers[index]!));
  }

  const refresh = async () => {
    tasks = await client().listTasks();
    providers.forEach((provider) => provider.setTasks(tasks));
    const actionable = tasks.filter((task) => !["COMPLETED", "CANCELLED"].includes(task.status)).length;
    status.update(tasks.length, actionable);
    logger.info("tasks_refreshed", { total: tasks.length, actionable });
  };

  const poller = new TaskPoller(refresh, () => vscode.window.state.focused,
    () => Boolean(config().get<string>("demoActorId")), {
      foregroundMs: config().get<number>("foregroundPollSeconds", 60) * 1000,
      backgroundMs: config().get<number>("backgroundPollSeconds", 300) * 1000,
    });
  poller.start();

  context.subscriptions.push(output, status, { dispose: () => poller.stop() },
    vscode.window.onDidChangeWindowState((state) => { if (state.focused) void poller.onFocus(); }),
    vscode.commands.registerCommand("sdlc.refreshTasks", async () => {
      try { await refresh(); } catch (error) { logger.error("refresh_failed", { message: safeMessage(error) }); void vscode.window.showErrorMessage("SDLC refresh failed. Open Diagnostics for details."); }
    }),
    vscode.commands.registerCommand("sdlc.openTask", async (taskId?: string) => {
      const selected = taskId ?? await chooseTask(tasks);
      if (!selected) return;
      try {
        const task = await client().getTask(selected);
        const panel = vscode.window.createWebviewPanel("sdlcTask", `Task ${task.scope.ticketId}`, vscode.ViewColumn.Active, { enableScripts: false });
        panel.webview.html = shell(panel.webview, `Task ${task.scope.ticketId}`, `<pre class="card">${escapeHtml(JSON.stringify(task, null, 2))}</pre>`);
      } catch (error) { logger.error("open_task_failed", { message: safeMessage(error) }); }
    }),
    vscode.commands.registerCommand("sdlc.openReport", async () => {
      const artifactId = await vscode.window.showInputBox({ title: "Artifact ID", prompt: "Example: ART-1", ignoreFocusOut: true });
      if (!artifactId) return;
      const versionText = await vscode.window.showInputBox({ title: "Artifact version", value: "1", validateInput: positiveInteger });
      if (!versionText) return;
      try { openReportPanel(`${artifactId} v${versionText}`, await client().getReport(artifactId, Number(versionText))); }
      catch (error) { logger.error("open_report_failed", { message: safeMessage(error) }); }
    }),
    vscode.commands.registerCommand("sdlc.approve", async () => {
      const taskId = await vscode.window.showInputBox({ title: "Task ID" }); if (!taskId) return;
      const artifactId = await vscode.window.showInputBox({ title: "Artifact ID" }); if (!artifactId) return;
      const artifactVersion = await askVersion("Artifact version"); if (!artifactVersion) return;
      const taskVersion = await askVersion("Current task version", true); if (taskVersion === undefined) return;
      openApprovalPanel({ taskId, artifactId, artifactVersion, taskVersion }, async () => {
        await client().approve({ taskId, artifactId, artifactVersion, expectedTaskVersion: taskVersion });
        await refresh();
        void vscode.window.showInformationMessage(`Approved ${artifactId} version ${artifactVersion}.`);
      });
    }),
    vscode.commands.registerCommand("sdlc.copyCopilotCommand", async () => {
      const task = await chooseTask(tasks);
      const command = task ? `/resume-workflow ${task}` : "/start-ticket DEMO-123";
      await vscode.env.clipboard.writeText(command);
      void vscode.window.showInformationMessage(`Copied: ${command}`);
    }),
    vscode.commands.registerCommand("sdlc.openMcpCenter", () => openMcpCenter()),
    vscode.commands.registerCommand("sdlc.checkMcpHealth", async () => {
      const results = await checkMcpHealth(client(), hasMcpConfig());
      const panel = vscode.window.createWebviewPanel("sdlcDiagnostics", "SDLC Diagnostics", vscode.ViewColumn.Active, { enableScripts: false });
      panel.webview.html = shell(panel.webview, "Diagnostics", results.map((result) =>
        `<section class="card"><h2>${result.ok ? "PASS" : "ACTION REQUIRED"} · ${escapeHtml(result.name)}</h2><p>${escapeHtml(result.detail)}</p></section>`).join(""));
      logger.info("diagnostics_completed", { passing: results.filter((result) => result.ok).length, total: results.length });
    }));

  logger.info("extension_activated", { views: viewIds.length });
}

export function deactivate(): void {}

async function chooseTask(tasks: WorkflowTask[]): Promise<string | undefined> {
  const picked = await vscode.window.showQuickPick(tasks.map((task) => ({
    label: `${task.scope.ticketId} · ${task.status}`, description: task.scope.repositoryAlias, taskId: task.taskId,
  })), { title: "Select persisted workflow task" });
  return picked?.taskId;
}

async function askVersion(title: string, allowZero = false): Promise<number | undefined> {
  const value = await vscode.window.showInputBox({ title, value: allowZero ? "0" : "1", validateInput: (text) => positiveInteger(text, allowZero) });
  return value === undefined ? undefined : Number(value);
}

function positiveInteger(value: string, allowZero = false): string | undefined {
  const number = Number(value);
  return Number.isInteger(number) && number >= (allowZero ? 0 : 1) ? undefined : "Enter a valid version number";
}

function hasMcpConfig(): boolean {
  return vscode.workspace.workspaceFolders?.some((folder) => existsSync(join(folder.uri.fsPath, ".vscode", "mcp.json"))) ?? false;
}

function openMcpCenter(): void {
  const panel = vscode.window.createWebviewPanel("sdlcMcpCenter", "Local MCP Center", vscode.ViewColumn.Active, { enableScripts: true });
  const example = JSON.stringify({ servers: { "sdlc-workflow": { type: "stdio", command: "node", args: ["<central-repo>/apps/workflow-mcp/dist/index.js"], env: { WORKFLOW_SERVICE_URL: "http://127.0.0.1:8080" } } } }, null, 2);
  const rows = [
    ["Workflow MCP", "Required", "Persist and resume SDLC state"],
    ["Jira MCP", "Internal adapter", "Ticket context and milestone comments"],
    ["Confluence MCP", "Internal adapter", "Read approved team knowledge"],
    ["GitHub MCP", "Internal adapter", "Repository, PR, and checks context"],
    ["Code graph / Understand Anything", "Optional experiment", "Local architecture and call relationships"],
  ];
  panel.webview.html = shell(panel.webview, "Local MCP Center", rows.map(([name, state, purpose]) =>
    `<section class="card"><h2>${escapeHtml(name!)}</h2><p><strong>${escapeHtml(state!)}</strong> — ${escapeHtml(purpose!)}</p></section>`).join("") +
    `<h2>Workspace configuration</h2><pre class="card">${escapeHtml(example)}</pre><button id="copy">Copy reviewed example</button>`,
    `const vscode=acquireVsCodeApi();document.getElementById('copy').addEventListener('click',()=>vscode.postMessage({type:'copy'}));`);
  panel.webview.onDidReceiveMessage(async (message) => {
    if (message?.type === "copy") { await vscode.env.clipboard.writeText(example); void vscode.window.showInformationMessage("MCP example copied. Review paths and secrets before saving."); }
  });
}

function safeMessage(error: unknown): string { return error instanceof Error ? error.message.replace(/(token|password|secret)=[^\s]+/gi, "$1=[redacted]") : "Unknown error"; }
