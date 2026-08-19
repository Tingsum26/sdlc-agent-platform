import * as vscode from "vscode";
import type { EpicSummary } from "../api/workflowClient.js";
import { toViewState, type Freshness } from "./viewState.js";
import { emptyItem, errorItem, loadingItem, safeMessage, statusIcon } from "./treeItems.js";
import type { ViewStateWithFreshness, WorkflowViewsClient } from "./types.js";

/** Epic view: the journey epics with their lifecycle status. */
export class EpicProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;
  private state: ViewStateWithFreshness<EpicSummary[]> = toViewState({ kind: "loading" });

  constructor(private readonly client: WorkflowViewsClient) {}

  getTreeItem(item: vscode.TreeItem): vscode.TreeItem { return item; }

  async refresh(): Promise<void> {
    try {
      const epics = await this.client.listEpics();
      this.state = toViewState({ kind: "data", data: epics, at: Date.now() });
    } catch (error) {
      this.state = toViewState({ kind: "error", message: safeMessage(error) });
    }
    this.changed.fire();
  }

  getChildren(): vscode.TreeItem[] {
    if (this.state.kind === "loading") return [loadingItem()];
    if (this.state.kind === "error") return [errorItem(this.state.message)];
    if (this.state.data.length === 0) return [emptyItem("No epics")];
    return this.state.data.map((epic) => this.epicItem(epic, this.state.freshness));
  }

  private epicItem(epic: EpicSummary, freshness: Freshness): vscode.TreeItem {
    const label = `${epic.epicId} · ${epic.title}`;
    const item = new vscode.TreeItem(label, vscode.TreeItemCollapsibleState.None);
    item.description = `${epic.status} · ${freshness}`;
    item.tooltip = `Journey ${epic.journeyId}\nVersion ${epic.version}\nFreshness: ${freshness}`;
    item.iconPath = statusIcon(epic.status);
    item.accessibilityInformation = { label: `${label}. Status ${epic.status}. Journey ${epic.journeyId}. Freshness ${freshness}.` };
    return item;
  }
}
