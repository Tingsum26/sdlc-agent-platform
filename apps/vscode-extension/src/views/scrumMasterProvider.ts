import * as vscode from "vscode";
import type { EpicResume } from "../api/workflowClient.js";
import { toViewState, type Freshness } from "./viewState.js";
import { emptyItem, errorItem, loadingItem, safeMessage, statusIcon } from "./treeItems.js";
import { FIRST_EPIC_ID, type ViewStateWithFreshness, type WorkflowViewsClient } from "./types.js";

/**
 * Scrum Master view: next-action hints for the first epic's tickets from the
 * epic resume, so the scrum master knows what to unblock first.
 */
export class ScrumMasterProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;
  private state: ViewStateWithFreshness<EpicResume> = toViewState({ kind: "loading" });

  constructor(private readonly client: WorkflowViewsClient) {}

  getTreeItem(item: vscode.TreeItem): vscode.TreeItem { return item; }

  async refresh(): Promise<void> {
    try {
      const resume = await this.client.getEpicResume(FIRST_EPIC_ID);
      this.state = toViewState({ kind: "data", data: resume, at: Date.now() });
    } catch (error) {
      this.state = toViewState({ kind: "error", message: safeMessage(error) });
    }
    this.changed.fire();
  }

  getChildren(): vscode.TreeItem[] {
    if (this.state.kind === "loading") return [loadingItem()];
    if (this.state.kind === "error") return [errorItem(this.state.message)];
    const tickets = this.state.data.tickets;
    if (tickets.length === 0) return [emptyItem("No next actions")];
    return tickets.map((entry) => this.ticketItem(entry, this.state.freshness));
  }

  private ticketItem(entry: EpicResume["tickets"][number], freshness: Freshness): vscode.TreeItem {
    const label = `${entry.ticket.ticketId} · ${entry.ticket.status}`;
    const item = new vscode.TreeItem(label, vscode.TreeItemCollapsibleState.None);
    item.description = entry.nextAction;
    item.tooltip = `Next action: ${entry.nextAction}\nVersion ${entry.ticket.version}\nFreshness: ${freshness}`;
    item.iconPath = statusIcon(entry.ticket.status);
    item.accessibilityInformation = { label: `${label}. ${entry.nextAction}. Status ${entry.ticket.status}.` };
    return item;
  }
}
