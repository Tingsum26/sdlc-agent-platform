import { cp, mkdir, readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { basename, dirname, join } from "node:path";
import * as vscode from "vscode";
import { loadAndValidateBundle, safeResolve } from "./bundleManifest.js";

interface InstalledBundle { version: string; root: string; installedAt: string }
const stateKey = "sdlc.installedCustomizationBundles";
const activeLocationsKey = "sdlc.activeCustomizationLocations";
const activeHookSettingsKey = "sdlc.activeCustomizationHookSettings";

// Content directories shipped wholesale with the bundle. The 2.0 manifest
// walker derives agents/skills/instructions/policies/evals lists as the
// manifest surface, but the installed bundle carries the full platform
// configuration, so every content directory is copied as-is.
const shippedContentDirs = ["hooks", "mcp", "policies", "templates", "evals"] as const;

interface HookEvent { event: string; action: string }

export async function installCustomizationBundle(context: vscode.ExtensionContext): Promise<void> {
  const selected = await vscode.window.showOpenDialog({ canSelectFolders: true, canSelectFiles: false, canSelectMany: false,
    title: "Select an extracted, reviewed SDLC customization bundle" });
  if (!selected?.[0]) return;
  const sourceRoot = selected[0].fsPath;
  const manifestPath = "central/manifests/bundle-manifest.json";
  const manifest = loadAndValidateBundle(sourceRoot, manifestPath);
  const destination = join(context.globalStorageUri.fsPath, "customizations", manifest.bundleVersion);
  const agentsRoot = join(destination, "agents");
  const skillsRoot = join(destination, "skills");
  const instructionsRoot = join(destination, "instructions");
  await Promise.all([mkdir(agentsRoot, { recursive: true }), mkdir(skillsRoot, { recursive: true }), mkdir(instructionsRoot, { recursive: true })]);

  for (const path of manifest.agents) await cp(safeResolve(sourceRoot, path), join(agentsRoot, basename(path)), { force: true });
  for (const path of manifest.skills) {
    const target = join(skillsRoot, skillInstallPath(path));
    await mkdir(dirname(target), { recursive: true });
    await cp(safeResolve(sourceRoot, path), target, { recursive: true, force: true });
  }
  for (const path of manifest.instructions.filter((value) => value.endsWith(".instructions.md"))) {
    await cp(safeResolve(sourceRoot, path), join(instructionsRoot, basename(path)), { force: true });
  }
  await mkdir(dirname(join(destination, manifestPath)), { recursive: true });
  await cp(safeResolve(sourceRoot, manifestPath), join(destination, manifestPath), { force: true });
  for (const dir of shippedContentDirs) {
    const sourceDir = safeResolve(sourceRoot, `central/${dir}`);
    if (!existsSync(sourceDir)) continue;
    const targetDir = join(destination, dir);
    await mkdir(dirname(targetDir), { recursive: true });
    await cp(sourceDir, targetDir, { recursive: true, force: true });
  }
  await activateBundleLocations(context, destination);

  const installed = context.globalState.get<InstalledBundle[]>(stateKey, []).filter((entry) => entry.version !== manifest.bundleVersion);
  installed.unshift({ version: manifest.bundleVersion, root: destination, installedAt: new Date().toISOString() });
  await context.globalState.update(stateKey, installed.slice(0, 5));
  void vscode.window.showInformationMessage(`Activated SDLC customization bundle ${manifest.bundleVersion}. Verify it in Chat Customizations diagnostics.`);
}

export async function rollbackCustomizationBundle(context: vscode.ExtensionContext): Promise<void> {
  const installed = context.globalState.get<InstalledBundle[]>(stateKey, []);
  if (installed.length < 2) { void vscode.window.showInformationMessage("No previous SDLC customization bundle is available."); return; }
  const choice = await vscode.window.showQuickPick(installed.slice(1).map((entry) => ({ label: entry.version, description: entry.installedAt, entry })),
    { title: "Select last-known-good customization bundle" });
  if (!choice) return;
  await activateBundleLocations(context, choice.entry.root);
  const reordered = [choice.entry, ...installed.filter((entry) => entry.version !== choice.entry.version)];
  await context.globalState.update(stateKey, reordered);
  void vscode.window.showInformationMessage(`Rolled back SDLC customizations to ${choice.entry.version}.`);
}

async function activateBundleLocations(context: vscode.ExtensionContext, root: string): Promise<void> {
  const agents = join(root, "agents");
  const skills = join(root, "skills");
  const instructions = join(root, "instructions");
  const previous = context.globalState.get<Record<string, string>>(activeLocationsKey, {});
  await addLocation("agentFilesLocations", agents, previous["agentFilesLocations"]);
  await addLocation("agentSkillsLocations", skills, previous["agentSkillsLocations"]);
  await addLocation("instructionsFilesLocations", instructions, previous["instructionsFilesLocations"]);
  await context.globalState.update(activeLocationsKey, {
    agentFilesLocations: agents, agentSkillsLocations: skills, instructionsFilesLocations: instructions,
  });
  await activateHooks(context, root);
}

// TODO(INTERNAL): INTERNAL-HOOKS-001 — Confirm the company Copilot policy
// allows VS Code agent hooks; replace the local echo no-op commands with the
// approved deterministic hook commands, and only then enable real hook
// execution. The declared events are still recorded in chat.agent.hooks so the
// activation surface is visible, but every command is a local no-op today.
async function activateHooks(context: vscode.ExtensionContext, root: string): Promise<void> {
  const chat = vscode.workspace.getConfiguration("chat.agent");
  let events: HookEvent[] = [];
  try {
    const manifest = JSON.parse(await readFile(join(root, "hooks", "hooks-manifest.json"), "utf8")) as { events?: HookEvent[] };
    events = manifest.events ?? [];
  } catch {
    events = [];
  }
  if (events.length === 0) return;
  const previous = context.globalState.get<Record<string, unknown>>(activeHookSettingsKey);
  const hookSettings: Record<string, unknown> = previous ? { ...previous } : {};
  for (const { event, action } of events) {
    hookSettings[event] = `echo ${action} >/dev/null && exit 0`;
  }
  await chat.update("hooks", hookSettings, vscode.ConfigurationTarget.Global);
  await context.globalState.update(activeHookSettingsKey, hookSettings);
}

async function addLocation(key: string, path: string, previous: string | undefined): Promise<void> {
  const chat = vscode.workspace.getConfiguration("chat");
  const current = chat.get<Record<string, boolean>>(key, {});
  const next = { ...current };
  if (previous) delete next[previous];
  next[path] = true;
  await chat.update(key, next, vscode.ConfigurationTarget.Global);
}

// Skills install relative to the skills root, keeping each skill's unique
// group/skill directory. Derived 2.0 paths are relative to the bundle source
// root (e.g. `central/skills/workflow/start-ticket/SKILL.md`), so take the
// substring after the first `skills/` segment; legacy 1.0 paths fall back to
// the basename exactly as before.
export function skillInstallPath(path: string): string {
  const marker = "/skills/";
  const index = path.indexOf(marker);
  if (index === -1) return basename(path);
  const relative = path.slice(index + marker.length);
  if (relative.length === 0 || /^[\\/]/.test(relative) || relative.split(/[\\/]/).some((segment) => segment === "..")) {
    throw new Error("Unsafe skill install path");
  }
  return relative;
}
