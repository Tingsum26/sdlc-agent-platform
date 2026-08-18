import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";

vi.mock("vscode", () => ({
  window: {
    showOpenDialog: vi.fn(),
    showInformationMessage: vi.fn(),
  },
  workspace: {
    getConfiguration: vi.fn(() => ({
      get: vi.fn(() => ({})),
      update: vi.fn().mockResolvedValue(undefined),
    })),
  },
  ConfigurationTarget: { Global: 1 },
}));

import * as vscode from "vscode";
import { installCustomizationBundle, skillInstallPath } from "../src/customization/bundleInstaller.js";

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
});
