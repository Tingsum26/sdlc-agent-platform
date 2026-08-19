import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";

const { configUpdates } = vi.hoisted(() => ({
  configUpdates: [] as Array<{ section: string | undefined; key: string; value: unknown }>,
}));

vi.mock("vscode", () => ({
  window: {
    showOpenDialog: vi.fn(),
    showInformationMessage: vi.fn(),
    showQuickPick: vi.fn(),
  },
  workspace: {
    getConfiguration: vi.fn((section?: string) => ({
      get: vi.fn((_key: string, fallback: unknown) => fallback),
      update: vi.fn((key: string, value: unknown) => {
        configUpdates.push({ section, key, value });
        return Promise.resolve();
      }),
    })),
  },
  ConfigurationTarget: { Global: 1 },
}));

import * as vscode from "vscode";
import { installCustomizationBundle, rollbackCustomizationBundle, skillInstallPath } from "../src/customization/bundleInstaller.js";

// The extension context's globalState must actually retain written values so a
// multi-step install → install → rollback scenario behaves like the real host.
function createStatefulContext(storageRoot: string): vscode.ExtensionContext {
  const store = new Map<string, unknown>();
  return {
    globalStorageUri: { fsPath: storageRoot },
    globalState: {
      get: vi.fn((key: string, fallback: unknown) => (store.has(key) ? store.get(key) : fallback)),
      update: vi.fn(async (key: string, value: unknown) => { store.set(key, value); }),
    },
  } as unknown as vscode.ExtensionContext;
}

// The last value written to a configuration section/key (the installer updates
// settings across installs, so the most recent write is the live one).
function writtenSetting(section: string, key: string): unknown {
  return configUpdates.filter((entry) => entry.section === section && entry.key === key).at(-1)?.value;
}

// A minimal 2.0 bundle source: one agent, one skill, one instruction, a hooks
// manifest with the given events, an mcp profiles file, and the counts summary.
function createSourceBundle(bundleId: string, events: Array<{ event: string; action: string }>): string {
  const sourceRoot = mkdtempSync(join(tmpdir(), `sdlc-${bundleId}-source-`));
  mkdirSync(join(sourceRoot, "central", "manifests"), { recursive: true });
  mkdirSync(join(sourceRoot, "central", "agents"), { recursive: true });
  mkdirSync(join(sourceRoot, "central", "instructions"), { recursive: true });
  mkdirSync(join(sourceRoot, "central", "skills", "workflow", "start-ticket"), { recursive: true });
  mkdirSync(join(sourceRoot, "central", "hooks"), { recursive: true });
  mkdirSync(join(sourceRoot, "central", "mcp"), { recursive: true });
  writeFileSync(join(sourceRoot, "central", "agents", "a.agent.md"), "a");
  writeFileSync(join(sourceRoot, "central", "instructions", "i.instructions.md"), "i");
  writeFileSync(join(sourceRoot, "central", "skills", "workflow", "start-ticket", "SKILL.md"), "s");
  writeFileSync(join(sourceRoot, "central", "hooks", "hooks-manifest.json"), JSON.stringify({ schemaVersion: "1.0", events }));
  writeFileSync(join(sourceRoot, "central", "mcp", "profiles.json"), JSON.stringify({ schemaVersion: "1.0", profiles: {} }));
  writeFileSync(join(sourceRoot, "central", "manifests", "bundle-manifest.json"), JSON.stringify({
    bundleId, schemaVersion: "2.0", agents: 1, skills: 1, instructions: 1, policies: 0, templates: 1, hooks: 1, profiles: 1,
  }));
  return sourceRoot;
}

describe("customization bundle installer", () => {
  it("keeps each 2.0 skill in its own group/skill directory instead of flattening SKILL.md names", async () => {
    const sourceRoot = mkdtempSync(join(tmpdir(), "sdlc-install-source-"));
    mkdirSync(join(sourceRoot, "central", "manifests"), { recursive: true });
    mkdirSync(join(sourceRoot, "central", "agents"), { recursive: true });
    mkdirSync(join(sourceRoot, "central", "instructions"), { recursive: true });
    mkdirSync(join(sourceRoot, "central", "skills", "workflow", "start-ticket"), { recursive: true });
    mkdirSync(join(sourceRoot, "central", "skills", "planning", "estimate"), { recursive: true });
    writeFileSync(join(sourceRoot, "central", "agents", "analyst.agent.md"), "analyst");
    writeFileSync(join(sourceRoot, "central", "instructions", "web.instructions.md"), "instructions");
    writeFileSync(join(sourceRoot, "central", "skills", "workflow", "start-ticket", "SKILL.md"), "start-ticket");
    writeFileSync(join(sourceRoot, "central", "skills", "planning", "estimate", "SKILL.md"), "estimate");
    writeFileSync(join(sourceRoot, "central", "manifests", "bundle-manifest.json"), JSON.stringify({
      bundleId: "test-bundle", schemaVersion: "2.0", agents: 1, skills: 2, instructions: 1, policies: 0, templates: 1,
    }));

    const storageRoot = mkdtempSync(join(tmpdir(), "sdlc-install-dest-"));
    vi.mocked(vscode.window.showOpenDialog).mockResolvedValue([{ fsPath: sourceRoot } as vscode.Uri]);
    const context = {
      globalStorageUri: { fsPath: storageRoot },
      globalState: { get: vi.fn((_key: string, fallback: unknown) => fallback), update: vi.fn().mockResolvedValue(undefined) },
    } as unknown as vscode.ExtensionContext;

    await installCustomizationBundle(context);

    const skillsRoot = join(storageRoot, "customizations", "test-bundle", "skills");
    expect(existsSync(join(skillsRoot, "workflow", "start-ticket", "SKILL.md"))).toBe(true);
    expect(existsSync(join(skillsRoot, "planning", "estimate", "SKILL.md"))).toBe(true);
    expect(readFileSync(join(skillsRoot, "workflow", "start-ticket", "SKILL.md"), "utf8")).toBe("start-ticket");
    expect(readFileSync(join(skillsRoot, "planning", "estimate", "SKILL.md"), "utf8")).toBe("estimate");

    const agentsRoot = join(storageRoot, "customizations", "test-bundle", "agents");
    const instructionsRoot = join(storageRoot, "customizations", "test-bundle", "instructions");
    expect(existsSync(join(agentsRoot, "analyst.agent.md"))).toBe(true);
    expect(existsSync(join(instructionsRoot, "web.instructions.md"))).toBe(true);
  });

  it("rejects a skill install path that traverses outside the skills root", () => {
    expect(() => skillInstallPath("central/skills/../../evil/SKILL.md")).toThrow(/unsafe/i);
  });

  it("installs hooks and profiles into the bundle and activates hook settings", async () => {
    const sourceRoot = createSourceBundle("hooks-bundle", [
      { event: "PreToolUse", action: "guard-dangerous-operations" },
    ]);
    const storageRoot = mkdtempSync(join(tmpdir(), "sdlc-hooks-dest-"));
    vi.mocked(vscode.window.showOpenDialog).mockResolvedValue([{ fsPath: sourceRoot } as vscode.Uri]);

    await installCustomizationBundle(createStatefulContext(storageRoot));

    const bundleRoot = join(storageRoot, "customizations", "hooks-bundle");
    expect(existsSync(join(bundleRoot, "hooks", "hooks-manifest.json"))).toBe(true);
    expect(existsSync(join(bundleRoot, "mcp", "profiles.json"))).toBe(true);
    expect(writtenSetting("chat.agent", "hooks")).toEqual({
      PreToolUse: "echo guard-dangerous-operations >/dev/null && exit 0",
    });
  });

  it("rolls back to a previous bundle and restores its hook settings", async () => {
    const v1Root = createSourceBundle("hooks-bundle-v1", [
      { event: "PreToolUse", action: "guard-dangerous-operations" },
      { event: "Stop", action: "verify-stage-output" },
    ]);
    const v2Root = createSourceBundle("hooks-bundle-v2", [
      { event: "PreToolUse", action: "guard-v2" },
    ]);
    const storageRoot = mkdtempSync(join(tmpdir(), "sdlc-rollback-dest-"));
    const context = createStatefulContext(storageRoot);

    vi.mocked(vscode.window.showOpenDialog).mockResolvedValueOnce([{ fsPath: v1Root } as vscode.Uri]);
    vi.mocked(vscode.window.showOpenDialog).mockResolvedValueOnce([{ fsPath: v2Root } as vscode.Uri]);
    await installCustomizationBundle(context);
    await installCustomizationBundle(context);

    // v2 install overwrote the PreToolUse command and carried the Stop entry
    // over from the previously recorded hook settings.
    expect(writtenSetting("chat.agent", "hooks")).toEqual({
      PreToolUse: "echo guard-v2 >/dev/null && exit 0",
      Stop: "echo verify-stage-output >/dev/null && exit 0",
    });

    vi.mocked(vscode.window.showQuickPick).mockImplementation(async (items) => items[0]);
    await rollbackCustomizationBundle(context);

    // Rolling back to v1 re-applies v1's hook settings (including the Stop
    // entry that v2's manifest does not declare).
    expect(writtenSetting("chat.agent", "hooks")).toEqual({
      PreToolUse: "echo guard-dangerous-operations >/dev/null && exit 0",
      Stop: "echo verify-stage-output >/dev/null && exit 0",
    });
  });
});
