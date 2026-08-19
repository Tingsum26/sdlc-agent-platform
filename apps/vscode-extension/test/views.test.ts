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

  it("epic view lists epics with status and freshness text", async () => {
    const client = { listEpics: vi.fn().mockResolvedValue([{ epicId: "EPIC-M2-1", title: "Account opening", journeyId: "ACCOUNT_OPENING", status: "ACTIVE", version: 3 }]) };
    const provider = new EpicProvider(client as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items[0]!.label).toContain("EPIC-M2-1");
    expect(items[0]!.description).toMatch(/^ACTIVE · (LIVE|DELAYED|STALE|OFFLINE)$/);
    expect(items[0]!.accessibilityInformation!.label).toMatch(/Freshness (LIVE|DELAYED|STALE|OFFLINE)/);
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
    expect(items[0]!.description).toMatch(/^Open the PR · (LIVE|DELAYED|STALE|OFFLINE)$/);
    expect(client.getEpicResume).toHaveBeenCalledWith("EPIC-M2-1");
  });

  it("customization view lists installed bundle versions plus commands", async () => {
    const store = { get: vi.fn().mockReturnValue([{ version: "2.0.0", root: "/bundles/2.0.0", installedAt: "2026-08-18T00:00:00Z" }]) };
    const provider = new CustomizationProvider(store as never);
    await provider.refresh();
    const items = provider.getChildren() as vscode.TreeItem[];
    expect(items.length).toBe(4);
    expect(items[0]!.label).toBe("2.0.0");
    expect(items[0]!.description).toMatch(/^2026-08-18T00:00:00Z · (LIVE|DELAYED|STALE|OFFLINE)$/);
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
    expect(items[0]!.description).toMatch(/^required · (LIVE|DELAYED|STALE|OFFLINE)$/);
    expect(items[1]!.description).toMatch(/^optional · (LIVE|DELAYED|STALE|OFFLINE)$/);
  });

  it("every provider item carries accessibilityInformation with text status and freshness", async () => {
    const epic = { epicId: "EPIC-M2-1", title: "Account opening", journeyId: "ACCOUNT_OPENING", status: "ACTIVE", version: 3 };
    const ticket = { ticketId: "M2-API-1", epicId: "EPIC-M2-1", channel: "API", status: "PR_OPEN", pendingChangeConfirmation: false, version: 5 };
    const client = {
      listTasks: vi.fn().mockResolvedValue([task]),
      listEpics: vi.fn().mockResolvedValue([epic]),
      getEpicResume: vi.fn().mockResolvedValue({ epic, tickets: [{ ticket, openTasks: [task], nextAction: "Open the PR" }], auditTrail: [] }),
      listTickets: vi.fn().mockResolvedValue([ticket]),
      listRepoTasks: vi.fn().mockResolvedValue([{ repoTaskId: "REPO-TASK-1", ticketId: "M2-API-1", repositoryAlias: "REPO_A", status: "MERGED", version: 2 }]),
      getIdentity: vi.fn().mockResolvedValue({ employeeId: "EMP-100", displayLabel: "Fictional Scrum Master", source: "ADMIN_BINDING" }),
      getPodMembers: vi.fn().mockResolvedValue([{ principalId: "P-1", employeeId: "EMP-201", displayLabel: "Fictional Developer", role: "DEVELOPER", onboardingStatus: "ONBOARDED" }]),
    };
    const providers: Array<{ refresh(): Promise<void>; getChildren(): vscode.TreeItem[] | Thenable<vscode.TreeItem[]> }> = [
      new MyWorkProvider(client as never),
      new ScrumMasterProvider(client as never),
      new EpicProvider(client as never),
      new TicketProvider(client as never),
      new IdentityPodProvider(client as never),
      new CustomizationProvider({ get: vi.fn().mockReturnValue([{ version: "2.0.0", root: "/bundles", installedAt: "2026-08-18T00:00:00Z" }]) } as never),
      new McpCenterProvider([{ id: "workflow-mcp", name: "Workflow MCP", required: true, skills: ["start-ticket"] }]),
    ];
    await Promise.all(providers.map((provider) => provider.refresh()));

    for (const provider of providers) {
      const items = (await provider.getChildren()) as vscode.TreeItem[];
      expect(items.length).toBeGreaterThan(0);
      for (const item of items) {
        expect(item.accessibilityInformation, `a11y missing on "${String(item.label)}"`).toBeDefined();
        expect(item.accessibilityInformation!.label.length).toBeGreaterThan(0);
      }
    }

    // Freshness and status are conveyed as text, not only via icon/color.
    const epicItems = (await providers[2]!.getChildren()) as vscode.TreeItem[];
    expect(String(epicItems[0]!.description)).toMatch(/^ACTIVE · (LIVE|DELAYED|STALE|OFFLINE)$/);
    expect(epicItems[0]!.accessibilityInformation!.label).toContain("Status ACTIVE.");
    expect(epicItems[0]!.accessibilityInformation!.label).toMatch(/Freshness (LIVE|DELAYED|STALE|OFFLINE)/);

    // The Ticket view's nested Repo Task children carry accessibility too.
    const ticketProvider = providers[3] as unknown as { getChildren(element?: vscode.TreeItem): vscode.TreeItem[] | Thenable<vscode.TreeItem[]> };
    const ticketItems = (await ticketProvider.getChildren()) as vscode.TreeItem[];
    const repoTaskItems = (await ticketProvider.getChildren(ticketItems[0])) as vscode.TreeItem[];
    expect(String(repoTaskItems[0]!.label)).toContain("REPO-TASK-1");
    expect(repoTaskItems[0]!.accessibilityInformation).toBeDefined();
  });

  it("empty and error states render explicit labeled items with accessibility", async () => {
    const empty = new MyWorkProvider({ listTasks: vi.fn().mockResolvedValue([]) } as never);
    await empty.refresh();
    const emptyItems = empty.getChildren() as vscode.TreeItem[];
    expect(String(emptyItems[0]!.label)).toBe("No actionable tasks");
    expect(emptyItems[0]!.accessibilityInformation).toBeDefined();

    const failing = new MyWorkProvider({ listTasks: vi.fn().mockRejectedValue(new Error("boom")) } as never);
    await failing.refresh();
    const errorItems = failing.getChildren() as vscode.TreeItem[];
    expect(String(errorItems[0]!.label)).toBe("Error: boom · retry with SDLC: Refresh Tasks");
    expect(errorItems[0]!.accessibilityInformation).toBeDefined();
  });
});
