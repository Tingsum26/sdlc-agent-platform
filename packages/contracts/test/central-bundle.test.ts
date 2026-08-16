import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(import.meta.dirname, "../../..");

describe("central customization bundle", () => {
  it("separates always-on instructions, policies, MCP catalog, and evals", () => {
    for (const path of [
      ".github/copilot-instructions.md",
      ".github/instructions/java-spring.instructions.md",
      ".github/instructions/web.instructions.md",
      "policies/stage-gates-v1.json",
      "policies/api-compatibility-v1.json",
      "mcp/catalog.json",
      "evals/importing-pod-members-contract.tests.ps1",
    ]) expect(existsSync(resolve(root, path)), path).toBe(true);
    expect(readFileSync(resolve(root, ".github/instructions/java-spring.instructions.md"), "utf8")).toMatch(/applyTo:.*\.java/);
  });

  it("publishes a versioned, non-secret inventory for VSIX installation", () => {
    const manifest = JSON.parse(readFileSync(resolve(root, "manifests/customization-bundle-v1.json"), "utf8"));
    expect(manifest.schemaVersion).toBe("1.0");
    expect(manifest.agents).toHaveLength(3);
    expect(manifest.skills).toEqual(expect.arrayContaining([
      ".github/skills/start-ticket", ".github/skills/resume-workflow", ".github/skills/prepare-pr", "skills/importing-pod-members",
    ]));
    expect(JSON.stringify(manifest)).not.toMatch(/token|password|cookie|company\.com/i);
  });
});
