import * as vscode from "vscode";
import type { WorkflowTask } from "../api/workflowClient.js";

export class TaskTreeProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;
  private tasks: WorkflowTask[] = [];

  constructor(private readonly viewId: string) {}

  setTasks(tasks: WorkflowTask[]): void { this.tasks = tasks; this.changed.fire(); }
  getTreeItem(item: vscode.TreeItem): vscode.TreeItem { return item; }

  getChildren(): vscode.TreeItem[] {
    if (this.viewId === "sdlc.mcpCenter") return [this.command("Open MCP onboarding", "sdlc.openMcpCenter")];
    if (this.viewId === "sdlc.diagnostics") return [this.command("Run diagnostics", "sdlc.checkMcpHealth")];
    if (this.viewId === "sdlc.customization") return [this.command("Copy /start-ticket command", "sdlc.copyCopilotCommand")];
    if (this.tasks.length === 0) return [new vscode.TreeItem("No persisted tasks")];
    return this.tasks.map((task) => {
      const item = new vscode.TreeItem(`${task.scope.ticketId} · ${task.status}`, vscode.TreeItemCollapsibleState.None);
      item.description = task.scope.repositoryAlias;
      item.tooltip = `${task.taskId}\nVersion ${task.version}\nUpdated ${task.updatedAt}`;
      item.command = { command: "sdlc.openTask", title: "Open task", arguments: [task.taskId] };
      item.contextValue = "sdlcTask";
      return item;
    });
  }

  private command(label: string, command: string): vscode.TreeItem {
    const item = new vscode.TreeItem(label);
    item.command = { command, title: label };
    return item;
  }
}
