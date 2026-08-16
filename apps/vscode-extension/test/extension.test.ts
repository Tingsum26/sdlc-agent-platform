import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(import.meta.dirname, "..");

describe("VSIX static boundaries", () => {
  it("declares every MVP view and command", () => {
    const manifest = JSON.parse(readFileSync(resolve(root, "package.json"), "utf8"));
    const viewIds = manifest.contributes.views.sdlcWorkbench.map((view: { id: string }) => view.id);
    expect(viewIds).toEqual(expect.arrayContaining([
      "sdlc.developer", "sdlc.scrumMaster", "sdlc.myWork", "sdlc.epic", "sdlc.ticket",
      "sdlc.repoTask", "sdlc.customization", "sdlc.mcpCenter", "sdlc.diagnostics",
    ]));
    expect(manifest.activationEvents).toContain("onStartupFinished");
  });

  it("contains no model invocation or direct persistence integration", () => {
    const source = readdirSync(resolve(root, "src"), { recursive: true, withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith(".ts"))
      .map((entry) => readFileSync(resolve(entry.parentPath, entry.name), "utf8")).join("\n");
    expect(source).not.toMatch(/vscode\.lm|selectChatModels|sendRequest|LanguageModelTool|MongoClient|GridFS|JiraClient/i);
  });
});
