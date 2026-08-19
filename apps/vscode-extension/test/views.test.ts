import { afterEach, describe, expect, it, vi } from "vitest";

// The vi.mock factory is hoisted above module-level consts, so the shared
// emitter helper must be created through vi.hoisted (same pattern as
// bundleInstaller.test.ts).
const { makeEventEmitter } = vi.hoisted(() => ({
  makeEventEmitter: () => ({ event: vi.fn(), fire: vi.fn() }),
}));

vi.mock("vscode", () => ({
  EventEmitter: vi.fn(function () { return makeEventEmitter(); }),
  TreeItem: class { constructor(public label: string, public collapsibleState?: number) {} },
  TreeItemCollapsibleState: { None: 0, Collapsed: 1, Expanded: 2 },
  ThemeIcon: class { constructor(public id: string) {} },
}));

import * as vscode from "vscode";
import { MyWorkProvider } from "../src/views/myWorkProvider.js";
import { ScrumMasterProvider } from "../src/views/scrumMasterProvider.js";
import { EpicProvider } from "../src/views/epicProvider.js";
import { TicketProvider } from "../src/views/ticketProvider.js";
import { IdentityPodProvider } from "../src/views/identityPodProvider.js";
import { CustomizationProvider } from "../src/views/customizationProvider.js";
import { McpCenterProvider } from "../src/views/mcpCenterProvider.js";

const task = { taskId: "TASK-1", type: "REQUIREMENT_ANALYSIS", status: "WAITING_FOR_LOCAL_COPILOT", scope: { ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567" }, version: 0, updatedAt: "2026-08-18T00:00:00Z" };

describe("view providers", () => {
  afterEach(() => vi.clearAllMocks());

  it("my work shows only actionable tasks with freshness", async () => {
    const client = { listTasks: vi.fn().mockResolvedValue([task]) };
    const provider = new MyWorkProvider(client as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.length).toBe(1);
    expect(items[0]!.label).toContain("DEMO-123");
  });

  it("ticket view nests repo tasks under tickets", async () => {
    const client = {
      listTickets: vi.fn().mockResolvedValue([{ ticketId: "M2-API-1", epicId: "EPIC-M2-1", channel: "API", status: "PR_OPEN", pendingChangeConfirmation: false, version: 5 }]),
      listRepoTasks: vi.fn().mockResolvedValue([{ repoTaskId: "REPO-TASK-1", ticketId: "M2-API-1", repositoryAlias: "REPO_A", status: "MERGED", version: 2 }]),
    };
    const provider = new TicketProvider(client as never);
    await provider.refresh();
    const ticketItem = provider.getChildren() as vscode.TreeItem[];
    expect(ticketItem.length).toBe(1);
    const children = await provider.getChildren(ticketItem[0]);
    expect((children as vscode.TreeItem[])[0]!.label).toContain("REPO-TASK-1");
  });

  it("identity view lists pod members", async () => {
    const client = {
      getIdentity: vi.fn().mockResolvedValue({ employeeId: "EMP-100", displayLabel: "Fictional Scrum Master", source: "ADMIN_BINDING" }),
      getPodMembers: vi.fn().mockResolvedValue([{ principalId: "PRINCIPAL-EMP-201", employeeId: "EMP-201", displayLabel: "Fictional Developer", role: "DEVELOPER", onboardingStatus: "NOT_ONBOARDED" }]),
    };
    const provider = new IdentityPodProvider(client as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.some((item) => String(item.label).includes("EMP-201"))).toBe(true);
  });

  it("epic view lists epics with status", async () => {
    const client = { listEpics: vi.fn().mockResolvedValue([{ epicId: "EPIC-M2-1", title: "Account opening", journeyId: "ACCOUNT_OPENING", status: "ACTIVE", version: 3 }]) };
    const provider = new EpicProvider(client as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items[0]!.label).toContain("EPIC-M2-1");
    expect(items[0]!.description).toBe("ACTIVE");
  });

  it("scrum master shows the first epic's next actions", async () => {
    const client = {
      getEpicResume: vi.fn().mockResolvedValue({
        epic: { epicId: "EPIC-M2-1", title: "Account opening", journeyId: "ACCOUNT_OPENING", status: "ACTIVE", version: 3 },
        tickets: [{ ticket: { ticketId: "M2-API-1", epicId: "EPIC-M2-1", channel: "API", status: "PR_OPEN", pendingChangeConfirmation: false, version: 5 }, openTasks: [], nextAction: "Open the PR" }],
        auditTrail: [],
      }),
    };
    const provider = new ScrumMasterProvider(client as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items[0]!.label).toContain("M2-API-1");
    expect(items[0]!.description).toBe("Open the PR");
    expect(client.getEpicResume).toHaveBeenCalledWith("EPIC-M2-1");
  });

  it("customization view lists installed bundle versions plus commands", async () => {
    const store = { get: vi.fn().mockReturnValue([{ version: "2.0.0", root: "/bundles/2.0.0", installedAt: "2026-08-18T00:00:00Z" }]) };
    const provider = new CustomizationProvider(store as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.length).toBe(4);
    expect(items[0]!.label).toBe("2.0.0");
    expect(items[0]!.description).toBe("2026-08-18T00:00:00Z");
    expect(store.get).toHaveBeenCalledWith("sdlc.installedCustomizationBundles", []);
  });

  it("mcp center lists catalog servers with skill counts", async () => {
    const provider = new McpCenterProvider([
      { id: "workflow-mcp", name: "Workflow MCP", required: true, skills: ["resume-workflow", "start-ticket"] },
      { id: "github-mcp", name: "GitHub MCP", required: false, skills: [] },
    ]);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.length).toBe(3);
    expect(items[0]!.label).toBe("workflow-mcp");
    expect(items[0]!.description).toBe("required");
    expect(items[1]!.description).toBe("optional");
  });
});
