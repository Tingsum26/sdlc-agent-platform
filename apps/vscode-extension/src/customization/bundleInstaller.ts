import { cp, mkdir, mkdtemp, readFile, rename, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import { basename, dirname, join } from "node:path";
import * as vscode from "vscode";
import { loadAndValidateBundle, rejectBundleSymlinks, safeResolve } from "./bundleManifest.js";

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
  const storageRoot = context.globalStorageUri.fsPath;
  await mkdir(storageRoot, { recursive: true });
  const sourceStaging = await mkdtemp(join(storageRoot, "customization-source-"));
  const stagedRoot = join(sourceStaging, "bundle");
  let candidate: string | undefined;
  let destination: string | undefined;
  let replacementInProgress = false;

  try {
    // Copy the untrusted selection once without dereferencing links. All later
    // parsing and copying use this extension-owned snapshot, closing the gap
    // between validation and use of the user-selected filesystem tree.
    await cp(sourceRoot, stagedRoot, { recursive: true, dereference: false });
    await rejectBundleSymlinks(stagedRoot);
    const manifest = loadAndValidateBundle(stagedRoot, manifestPath);
    const customizationsRoot = join(storageRoot, "customizations");
    await mkdir(customizationsRoot, { recursive: true });
    candidate = await mkdtemp(join(customizationsRoot, ".bundle-install-"));
    const agentsRoot = join(candidate, "agents");
    const skillsRoot = join(candidate, "skills");
    const instructionsRoot = join(candidate, "instructions");
    await Promise.all([mkdir(agentsRoot, { recursive: true }), mkdir(skillsRoot, { recursive: true }), mkdir(instructionsRoot, { recursive: true })]);

    for (const path of manifest.agents) await cp(safeResolve(stagedRoot, path), join(agentsRoot, basename(path)), { force: true });
    for (const path of manifest.skills) {
      const target = join(skillsRoot, skillInstallPath(path));
      await mkdir(dirname(target), { recursive: true });
      await cp(safeResolve(stagedRoot, path), target, { recursive: true, force: true });
    }
    for (const path of manifest.instructions.filter((value) => value.endsWith(".instructions.md"))) {
      await cp(safeResolve(stagedRoot, path), join(instructionsRoot, basename(path)), { force: true });
    }
    await mkdir(dirname(join(candidate, manifestPath)), { recursive: true });
    await cp(safeResolve(stagedRoot, manifestPath), join(candidate, manifestPath), { force: true });
    for (const dir of shippedContentDirs) {
      const sourceDir = safeResolve(stagedRoot, `central/${dir}`);
      if (!existsSync(sourceDir)) continue;
      const targetDir = join(candidate, dir);
      await mkdir(dirname(targetDir), { recursive: true });
      await cp(sourceDir, targetDir, { recursive: true, force: true });
    }
    await rejectBundleSymlinks(candidate);

    destination = join(customizationsRoot, manifest.bundleVersion);
    replacementInProgress = true;
    await rm(destination, { recursive: true, force: true });
    await rename(candidate, destination);
    candidate = undefined;
    await rejectBundleSymlinks(destination);
    replacementInProgress = false;
    await activateBundleLocations(context, destination);

    const installed = context.globalState.get<InstalledBundle[]>(stateKey, []).filter((entry) => entry.version !== manifest.bundleVersion);
    installed.unshift({ version: manifest.bundleVersion, root: destination, installedAt: new Date().toISOString() });
    await context.globalState.update(stateKey, installed.slice(0, 5));
    void vscode.window.showInformationMessage(`Activated SDLC customization bundle ${manifest.bundleVersion}. Verify it in Chat Customizations diagnostics.`);
  } catch (error) {
    if (candidate) await rm(candidate, { recursive: true, force: true });
    if (replacementInProgress && destination) await rm(destination, { recursive: true, force: true });
    throw error;
  } finally {
    await rm(sourceStaging, { recursive: true, force: true });
  }
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

// The exact command recorded for a validated hook entry. JSON string quoting is
// accepted by both cmd.exe and POSIX shells, so bundle paths with spaces cannot
// alter the command that invokes the installed Node no-op shim.
export function hookCommand(root: string, action: string): string {
  return `${JSON.stringify(process.execPath)} ${JSON.stringify(join(root, "hooks", "run-hook.mjs"))} ${JSON.stringify(action)}`;
}

const hookEventNames = ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PreCompact", "Stop"] as const;
const hookActionPattern = /^[a-zA-Z0-9][a-zA-Z0-9._-]*$/;

function isValidHookEntry(entry: unknown): entry is HookEvent {
  if (typeof entry !== "object" || entry === null) return false;
  const { event, action } = entry as Record<string, unknown>;
  return typeof event === "string" && (hookEventNames as readonly string[]).includes(event)
    && typeof action === "string" && hookActionPattern.test(action);
}

// Reads and validates the target bundle's hooks manifest. A missing manifest,
// unparseable JSON, or a non-array `events` field yields no entries rather than
// aborting activation; invalid entries are skipped individually.
async function readHookEntries(root: string): Promise<HookEvent[]> {
  try {
    const manifest = JSON.parse(await readFile(join(root, "hooks", "hooks-manifest.json"), "utf8")) as { events?: unknown };
    if (!Array.isArray(manifest.events)) return [];
    return manifest.events.filter(isValidHookEntry);
  } catch {
    return [];
  }
}

// TODO(INTERNAL): INTERNAL-HOOKS-001 — Confirm the company Copilot policy
// allows VS Code agent hooks; replace the local echo no-op commands with the
// approved deterministic hook commands, and only then enable real hook
// execution. The declared events are still recorded in chat.agent.hooks so the
// activation surface is visible, but every command is a local no-op today.
// NOTE: `echo … >/dev/null && exit 0` is POSIX-shell syntax; real hook commands
// must be invoked platform-safely (e.g. a Node shim shipped in the bundle or a
// per-OS command builder), never as a bare POSIX pipeline on Windows.
async function activateHooks(context: vscode.ExtensionContext, root: string): Promise<void> {
  // Merge/stale semantics: chat.agent.hooks is the user's live config and may
  // hold user-managed keys that must never be touched. The installer manages
  // only the keys it recorded in globalState (activeHookSettingsKey) for the
  // previously active bundle; every activation recomputes them for the target
  // bundle: start from the LIVE config, delete the previously recorded keys,
  // add this bundle's validated entries, and write back. Stale installer
  // entries are therefore removed on rollback or when a bundle declares no
  // events, while user-managed keys are preserved.
  const chat = vscode.workspace.getConfiguration("chat.agent");
  const live = chat.get<Record<string, unknown>>("hooks", {});
  const hookSettings: Record<string, unknown> = live && typeof live === "object" && !Array.isArray(live) ? { ...live } : {};
  const recorded = context.globalState.get<Record<string, unknown>>(activeHookSettingsKey, {});
  for (const key of Object.keys(recorded)) delete hookSettings[key];

  const nextRecorded: Record<string, unknown> = {};
  for (const { event, action } of await readHookEntries(root)) {
    hookSettings[event] = hookCommand(root, action);
    nextRecorded[event] = hookCommand(root, action);
  }

  await chat.update("hooks", hookSettings, vscode.ConfigurationTarget.Global);
  await context.globalState.update(activeHookSettingsKey, nextRecorded);
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
