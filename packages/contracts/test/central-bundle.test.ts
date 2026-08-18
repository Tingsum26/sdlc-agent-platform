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
const expectedSkills = [
  "start-epic", "join-epic", "change-epic", "start-ticket", "resume-workflow", "import-pod-members",
  "analyze-code-context", "grill-requirement", "assess-api-compatibility",
  "design-solution", "plan-change", "adr",
  "implement-task", "java-development", "web-development", "ios-development", "android-development",
  "generate-tests", "plan-manual-e2e", "record-manual-e2e", "review-accessibility", "review-analytics-tagging",
  "prepare-pr", "review-pr",
  "onboard-repository", "onboard-journey", "sync-onboarding", "analyze-http-call-graph",
  "analyze-epic-risk", "prepare-standup", "find-blockers", "check-release-readiness", "draft-jira-update",
];

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

  it("contains all 33 skills with valid frontmatter", () => {
    const files = readdirSync(resolve(root, "central/skills"), { recursive: true } as never)
      .filter((name) => String(name).endsWith("SKILL.md"));
    expect(files).toHaveLength(33);
    for (const skill of expectedSkills) {
      const content = readFileSync(resolve(root, `central/skills/${skillGroup(skill)}/${skill}/SKILL.md`), "utf8");
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
});

function skillGroup(skill: string): string {
  if (["start-epic", "join-epic", "change-epic", "start-ticket", "resume-workflow", "import-pod-members"].includes(skill)) return "workflow";
  if (["analyze-code-context", "grill-requirement", "assess-api-compatibility"].includes(skill)) return "analysis";
  if (["design-solution", "plan-change", "adr"].includes(skill)) return "design";
  if (["implement-task", "java-development", "web-development", "ios-development", "android-development"].includes(skill)) return "implement";
  if (["generate-tests", "plan-manual-e2e", "record-manual-e2e", "review-accessibility", "review-analytics-tagging"].includes(skill)) return "test";
  if (["prepare-pr", "review-pr"].includes(skill)) return "review";
  if (["onboard-repository", "onboard-journey", "sync-onboarding", "analyze-http-call-graph"].includes(skill)) return "onboard";
  return "sm";
}
