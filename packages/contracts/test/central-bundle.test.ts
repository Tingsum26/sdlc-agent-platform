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
      "central/policies/stage-gates.json",
      "central/policies/api-backward-compatibility.json",
      "central/mcp/catalog.json",
      "central/evals/agents-behavior.md",
    ]) expect(existsSync(resolve(root, path)), path).toBe(true);
  });

  it("publishes a versioned, non-secret inventory for VSIX installation", () => {
    const manifest = JSON.parse(readFileSync(resolve(root, "central/manifests/bundle-manifest.json"), "utf8"));
    expect(manifest.schemaVersion).toBe("2.0");
    expect(manifest.agents).toBe(13);
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
    expect(manifest.templates).toBe(20);
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

  it("manifest counts include hooks and profiles", () => {
    const manifest = JSON.parse(readFileSync(`${root}/central/manifests/bundle-manifest.json`, "utf8"));
    expect(manifest.hooks).toBe(1);
    expect(manifest.profiles).toBe(1);
  });

  it("declares hooks for deterministic lifecycle events only", () => {
    const hooks = JSON.parse(readFileSync(`${root}/central/hooks/hooks-manifest.json`, "utf8"));
    expect(hooks.schemaVersion).toBe("1.0");
    expect(hooks.events.map((hook: { event: string }) => hook.event).sort()).toEqual(["PostToolUse", "PreCompact", "PreToolUse", "SessionStart", "Stop", "UserPromptSubmit"]);
    for (const hook of hooks.events) {
      expect(["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PreCompact", "Stop"]).toContain(hook.event);
      expect(hook.action).toBeTruthy();
      expect(hook.deterministic).toBe(true);
    }
  });

  it("defines role profiles referencing real skills and catalog servers", () => {
    const profiles = JSON.parse(readFileSync(`${root}/central/mcp/profiles.json`, "utf8")).profiles;
    const catalog = JSON.parse(readFileSync(`${root}/central/mcp/catalog.json`, "utf8"));
    const serverIds = catalog.servers.map((server: { id: string }) => server.id);
    const skills = readdirSync(`${root}/central/skills`, { recursive: true } as never)
      .filter((name) => String(name).endsWith("SKILL.md"))
      .map((name) => String(name).split(/[\\/]/).slice(-2, -1)[0]);
    expect(Object.keys(profiles)).toHaveLength(5);
    for (const profile of Object.values(profiles) as Array<{ skills: string[]; servers: string[] }>) {
      for (const skill of profile.skills) expect(skills).toContain(skill);
      for (const server of profile.servers) expect(serverIds).toContain(server);
    }
  });

  it("review-pr mandates residual risks in its output contract", () => {
    const content = readFileSync(`${root}/central/skills/review/review-pr/SKILL.md`, "utf8");
    expect(content).toMatch(/residual risks/i);
  });
});
