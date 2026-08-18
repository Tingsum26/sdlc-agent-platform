import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(import.meta.dirname, "../../..");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("central Copilot customizations", () => {
  const skillPaths: Record<string, string> = {
    "start-ticket": "central/skills/workflow/start-ticket/SKILL.md",
    "resume-workflow": "central/skills/workflow/resume-workflow/SKILL.md",
    "prepare-pr": "central/skills/review/prepare-pr/SKILL.md",
  };
  for (const [name, path] of Object.entries(skillPaths)) {
    it(`${name} has valid skill metadata and safety boundaries`, () => {
      const source = read(path);
      expect(source).toMatch(new RegExp(`^---\\r?\\nname: ${name}\\r?\\ndescription: `, "m"));
      expect(source).toMatch(/human|用户|人工|confirm|approv/i);
      expect(source).not.toMatch(/cloud agent|background agent|Jenkins.*scan|MongoDB driver/i);
    });
  }

  for (const name of ["requirement-analyst", "solution-architect", "pr-reviewer"]) {
    it(`${name} is a bounded agent definition`, () => {
      const source = read(`central/agents/${name}.agent.md`);
      expect(source).toMatch(/^---\r?\nname:/);
      expect(source).toMatch(/tools:/);
      expect(source).toMatch(/Workflow MCP|workflow_/i);
      expect(source).not.toMatch(/tools:.*(?:edit|terminal|execute|MongoDB)/i);
    });
  }

  it("the reviewer remains read-only and reports evidence", () => {
    const source = read("central/agents/pr-reviewer.agent.md");
    expect(source).toMatch(/read.only|只读/i);
    expect(source).toMatch(/evidence|证据/i);
    expect(source).toMatch(/severity|严重/i);
  });
});

describe("copilot format intersection", () => {
  it("uses no Claude-only fields in agents or skills", () => {
    const scan = (dir: string) => readdirSync(dir, { recursive: true } as never)
      .filter((name) => String(name).endsWith(".agent.md") || String(name).endsWith("SKILL.md"))
      .map((name) => readFileSync(resolve(dir, String(name)), "utf8"))
      .join("\n");
    const content = scan(resolve(root, "central/agents")) + scan(resolve(root, "central/skills"));
    expect(content).not.toMatch(/allowed-tools|agent-instructions:/);
    expect(content).not.toContain("claude:");
  });

  it("every agent declares the sdlc-workflow MCP tools and a non-empty tool list", () => {
    const files = readdirSync(resolve(root, "central/agents")).filter((name) => name.endsWith(".agent.md"));
    for (const name of files) {
      const content = readFileSync(resolve(root, "central/agents", name), "utf8");
      expect(content).toMatch(/workflow_[a-z_]+/);
      expect(content).not.toMatch(/tools:\s*\[\s*\]/);
    }
  });
});
