import * as vscode from "vscode";
import type { RepoTaskSummary, TicketSummary } from "../api/workflowClient.js";
import { toViewState, type Freshness } from "./viewState.js";
import { emptyItem, errorItem, loadingItem, safeMessage, statusIcon } from "./treeItems.js";
import { FIRST_EPIC_ID, type ViewStateWithFreshness, type WorkflowViewsClient } from "./types.js";

/** A ticket row that knows its ticket id so getChildren can load its repo tasks. */
class TicketItem extends vscode.TreeItem {
  constructor(label: string, public readonly ticketId: string) {
    super(label, vscode.TreeItemCollapsibleState.Collapsed);
  }
}

/**
 * Ticket view: the first epic's tickets, each expandable to its repo tasks
 * (loaded on demand via listRepoTasks).
 */
export class TicketProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;
  private state: ViewStateWithFreshness<TicketSummary[]> = toViewState({ kind: "loading" });

  constructor(private readonly client: WorkflowViewsClient) {}

  getTreeItem(item: vscode.TreeItem): vscode.TreeItem { return item; }

  async refresh(): Promise<void> {
    try {
      const tickets = await this.client.listTickets(FIRST_EPIC_ID);
      this.state = toViewState({ kind: "data", data: tickets, at: Date.now() });
    } catch (error) {
      this.state = toViewState({ kind: "error", message: safeMessage(error) });
    }
    this.changed.fire();
  }

  getChildren(element?: vscode.TreeItem): vscode.TreeItem[] | Thenable<vscode.TreeItem[]> {
    if (element instanceof TicketItem) return this.repoTaskItems(element.ticketId);
    return this.rootItems();
  }

  private rootItems(): vscode.TreeItem[] {
    if (this.state.kind === "loading") return [loadingItem()];
    if (this.state.kind === "error") return [errorItem(this.state.message)];
    if (this.state.data.length === 0) return [emptyItem("No tickets")];
    return this.state.data.map((ticket) => this.ticketItem(ticket, this.state.freshness));
  }

  private ticketItem(ticket: TicketSummary, freshness: Freshness): TicketItem {
    const label = `${ticket.ticketId} · ${ticket.status}`;
    const item = new TicketItem(label, ticket.ticketId);
    item.description = `${ticket.channel} · ${freshness}`;
    item.tooltip = `Channel ${ticket.channel}\nVersion ${ticket.version}\nFreshness: ${freshness}`;
    item.iconPath = statusIcon(ticket.status);
    item.accessibilityInformation = { label: `${label}. Channel ${ticket.channel}. Status ${ticket.status}. Freshness ${freshness}.` };
    return item;
  }

  private async repoTaskItems(ticketId: string): Promise<vscode.TreeItem[]> {
    try {
      const tasks = await this.client.listRepoTasks(ticketId);
      if (tasks.length === 0) return [emptyItem("No repo tasks")];
      return tasks.map((task) => this.repoTaskItem(task));
    } catch (error) {
      return [errorItem(safeMessage(error))];
    }
  }

  private repoTaskItem(task: RepoTaskSummary): vscode.TreeItem {
    const label = `${task.repoTaskId} · ${task.status}`;
    const item = new vscode.TreeItem(label, vscode.TreeItemCollapsibleState.None);
    item.description = task.repositoryAlias;
    item.tooltip = `Version ${task.version}`;
    item.iconPath = statusIcon(task.status);
    item.accessibilityInformation = { label: `${label}. ${task.repositoryAlias}. Status ${task.status}.` };
    return item;
  }
}
