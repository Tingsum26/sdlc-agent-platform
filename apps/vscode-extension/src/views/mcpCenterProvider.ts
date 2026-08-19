import * as vscode from "vscode";
import { toViewState } from "./viewState.js";
import { commandItem, emptyItem, errorItem, loadingItem, safeMessage } from "./treeItems.js";
import type { McpCatalogEntry, ViewStateWithFreshness } from "./types.js";

/**
 * MCP Center view: catalog servers with their required/optional status and
 * skill counts, plus the onboarding command.
 */
export class McpCenterProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;
  private state: ViewStateWithFreshness<McpCatalogEntry[]> = toViewState({ kind: "loading" });

  constructor(private readonly catalog: McpCatalogEntry[]) {}

  getTreeItem(item: vscode.TreeItem): vscode.TreeItem { return item; }

  async refresh(): Promise<void> {
    try {
      this.state = toViewState({ kind: "data", data: this.catalog, at: Date.now() });
    } catch (error) {
      this.state = toViewState({ kind: "error", message: safeMessage(error) });
    }
    this.changed.fire();
  }

  getChildren(): vscode.TreeItem[] {
    if (this.state.kind === "loading") return [loadingItem()];
    if (this.state.kind === "error") return [errorItem(this.state.message)];
    const rows = this.state.data.map((entry) => this.serverItem(entry));
    if (rows.length === 0) rows.push(emptyItem("No catalog servers"));
    rows.push(commandItem("Open MCP onboarding", "sdlc.openMcpCenter"));
    return rows;
  }

  private serverItem(entry: McpCatalogEntry): vscode.TreeItem {
    const item = new vscode.TreeItem(entry.id, vscode.TreeItemCollapsibleState.None);
    item.description = entry.required ? "required" : "optional";
    item.tooltip = `${entry.name} · ${entry.skills.length} skills: ${entry.skills.join(", ")}`;
    item.iconPath = new vscode.ThemeIcon("server");
    item.accessibilityInformation = {
      label: `${entry.id}. ${entry.required ? "Required" : "Optional"}. ${entry.skills.length} skills.`,
    };
    return item;
  }
}
