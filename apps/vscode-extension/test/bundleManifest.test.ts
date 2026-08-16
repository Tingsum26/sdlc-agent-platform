import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadAndValidateBundle } from "../src/customization/bundleManifest.js";

describe("customization bundle manifest", () => {
  it("accepts a versioned inventory and resolves only files inside the bundle", () => {
    const root = mkdtempSync(join(tmpdir(), "sdlc-bundle-"));
    mkdirSync(join(root, ".github", "agents"), { recursive: true });
    mkdirSync(join(root, ".github", "skills", "start-ticket"), { recursive: true });
    writeFileSync(join(root, ".github", "agents", "analyst.agent.md"), "safe");
    writeFileSync(join(root, ".github", "skills", "start-ticket", "SKILL.md"), "safe");
    writeFileSync(join(root, "manifest.json"), JSON.stringify({
      schemaVersion: "1.0", bundleVersion: "1.2.3",
      agents: [".github/agents/analyst.agent.md"], skills: [".github/skills/start-ticket"], instructions: [],
    }));
    expect(loadAndValidateBundle(root, "manifest.json").bundleVersion).toBe("1.2.3");
  });

  it("rejects traversal and secret-like manifest fields", () => {
    const root = mkdtempSync(join(tmpdir(), "sdlc-bundle-"));
    writeFileSync(join(root, "manifest.json"), JSON.stringify({
      schemaVersion: "1.0", bundleVersion: "1.0.0", agents: ["../escape.agent.md"], skills: [], instructions: [], token: "bad",
    }));
    expect(() => loadAndValidateBundle(root, "manifest.json")).toThrow(/unsafe|secret/i);
  });
});
