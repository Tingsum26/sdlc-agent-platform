import { existsSync, readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(import.meta.dirname, "../../..");

const expectedAgents = [
  "epic-delivery-analyst", "delivery-coordinator", "requirement-analyst",
  "code-context-analyst", "solution-architect", "planner", "java-implementer",
  "web-implementer", "ios-implementer", "android-implementer", "test-designer",
  "accessibility-qa", "pr-reviewer",
];
const skillDirectories: Record<string, string> = {
  "start-epic": "workflow", "join-epic": "workflow", "change-epic": "workflow",
  "start-ticket": "workflow", "resume-workflow": "workflow", "import-pod-members": "workflow",
  "analyze-code-context": "analysis", "grill-requirement": "analysis", "assess-api-compatibility": "analysis",
  "design-solution": "design", "plan-change": "design", "adr": "design",
  "implement-task": "implement", "java-development": "implement", "web-development": "implement",
  "ios-development": "implement", "android-development": "implement",
  "generate-tests": "test", "plan-manual-e2e": "test", "record-manual-e2e": "test",
  "review-accessibility": "test", "review-analytics-tagging": "test",
  "prepare-pr": "review", "review-pr": "review",
  "onboard-repository": "onboard", "onboard-journey": "onboard", "sync-onboarding": "onboard", "analyze-http-call-graph": "onboard",
  "analyze-epic-risk": "sm", "prepare-standup": "sm", "find-blockers": "sm",
  "check-release-readiness": "sm", "draft-jira-update": "sm",
};
const expectedSkills = Object.keys(skillDirectories);

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

describe("central catalog", () => {
  it("contains all 13 agents with frontmatter", () => {
    const dir = resolve(root, "central/agents");
    expect(existsSync(dir)).toBe(true);
    const files = readdirSync(dir).filter((name) => name.endsWith(".agent.md"));
    expect(files).toHaveLength(13);
    for (const agent of expectedAgents) {
      const content = readFileSync(resolve(dir, `${agent}.agent.md`), "utf8");
      expect(content).toContain(`name: ${agent}`);
      expect(content).toContain("description:");
    }
  });

  it("manifest counts match the catalog", () => {
    const manifest = JSON.parse(readFileSync(`${root}/central/manifests/bundle-manifest.json`, "utf8"));
    expect(manifest.agents).toBe(13);
    expect(manifest.skills).toBe(33);
    expect(manifest.instructions).toBe(19);
    expect(manifest.policies).toBe(15);
    expect(manifest.templates).toBe(19);
    expect(existsSync(`${root}/${manifest.referencesFile}`)).toBe(true);
  });

  it("contains all 33 skills with valid frontmatter", () => {
    const files = readdirSync(`${root}/central/skills`, { recursive: true } as never)
      .filter((name) => String(name).endsWith("SKILL.md"));
    expect(files).toHaveLength(33);
    for (const skill of expectedSkills) {
      const group = skillDirectories[skill];
      if (!group) throw new Error(`No directory mapping for skill: ${skill}`);
      const content = readFileSync(`${root}/central/skills/${group}/${skill}/SKILL.md`, "utf8");
      expect(content).toContain(`name: ${skill}`);
      expect(content).toContain("description:");
      expect(content).toContain("version:");
    }
  });

  it("has a license-traceable REFERENCES file", () => {
    const content = readFileSync(resolve(root, "central/REFERENCES.md"), "utf8");
    expect(content).toContain("Apache-2.0");
    expect(content).toContain("MIT");
    expect(content).toContain("never copied");
  });

  it("every copied reference row carries an SPDX license and concept-only repos stay out of content", () => {
    const references = readFileSync(`${root}/central/REFERENCES.md`, "utf8");
    const tableSection = references.split("## Concept-only")[0];
    const rows = tableSection.split("\n").filter((line) => /^\| [a-zA-Z0-9_.-]+\/[a-zA-Z0-9_.-]+ /.test(line));
    for (const row of rows) {
      expect(row).toMatch(/ (MIT|Apache-2\.0) /);
    }
    const catalogContent = ["agents", "skills"]
      .filter((dir) => existsSync(`${root}/central/${dir}`))
      .map((dir) =>
        readdirSync(`${root}/central/${dir}`, { recursive: true } as never)
          .filter((name) => String(name).endsWith(".md"))
          .map((name) => readFileSync(`${root}/central/${dir}/${name}`, "utf8"))
          .join("\n")).join("\n");
    for (const conceptOnly of ["anthropics/skills", "vercel-labs/agent-skills", "ComposioHQ/awesome-claude-skills"]) {
      expect(catalogContent).not.toContain(conceptOnly);
    }
  });
});
