import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(import.meta.dirname, "../../..");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("central Copilot customizations", () => {
  for (const name of ["start-ticket", "resume-workflow", "prepare-pr"]) {
    it(`${name} has valid skill metadata and safety boundaries`, () => {
      const source = read(`.github/skills/${name}/SKILL.md`);
      expect(source).toMatch(new RegExp(`^---\\r?\\nname: ${name}\\r?\\ndescription: Use when`, "m"));
      expect(source).toMatch(/human|用户|人工/i);
      expect(source).toMatch(/approval|确认|批准/i);
      expect(source).not.toMatch(/cloud agent|background agent|Jenkins.*scan|MongoDB driver/i);
    });
  }

  for (const name of ["requirement-analyst", "solution-architect", "pr-reviewer"]) {
    it(`${name} is a bounded agent definition`, () => {
      const source = read(`.github/agents/${name}.agent.md`);
      expect(source).toMatch(/^---\r?\nname:/);
      expect(source).toMatch(/tools:/);
      expect(source).toMatch(/Workflow MCP|workflow_/i);
      expect(source).not.toMatch(/tools:.*(?:edit|terminal|execute|MongoDB)/i);
    });
  }

  it("the reviewer remains read-only and reports evidence", () => {
    const source = read(".github/agents/pr-reviewer.agent.md");
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
