import { cp, mkdir } from "node:fs/promises";
import { basename, dirname, join } from "node:path";
import * as vscode from "vscode";
import { loadAndValidateBundle, safeResolve } from "./bundleManifest.js";

interface InstalledBundle { version: string; root: string; installedAt: string }
const stateKey = "sdlc.installedCustomizationBundles";
const activeLocationsKey = "sdlc.activeCustomizationLocations";

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
  for (const path of manifest.skills) await cp(safeResolve(sourceRoot, path), join(skillsRoot, basename(path)), { recursive: true, force: true });
  for (const path of manifest.instructions.filter((value) => value.endsWith(".instructions.md"))) {
    await cp(safeResolve(sourceRoot, path), join(instructionsRoot, basename(path)), { force: true });
  }
  await mkdir(dirname(join(destination, manifestPath)), { recursive: true });
  await cp(safeResolve(sourceRoot, manifestPath), join(destination, manifestPath), { force: true });
  await activateBundleLocations(context, agentsRoot, skillsRoot, instructionsRoot);

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
  await activateBundleLocations(context, join(choice.entry.root, "agents"), join(choice.entry.root, "skills"), join(choice.entry.root, "instructions"));
  const reordered = [choice.entry, ...installed.filter((entry) => entry.version !== choice.entry.version)];
  await context.globalState.update(stateKey, reordered);
  void vscode.window.showInformationMessage(`Rolled back SDLC customizations to ${choice.entry.version}.`);
}

async function activateBundleLocations(context: vscode.ExtensionContext, agents: string, skills: string, instructions: string): Promise<void> {
  const previous = context.globalState.get<Record<string, string>>(activeLocationsKey, {});
  await addLocation("agentFilesLocations", agents, previous["agentFilesLocations"]);
  await addLocation("agentSkillsLocations", skills, previous["agentSkillsLocations"]);
  await addLocation("instructionsFilesLocations", instructions, previous["instructionsFilesLocations"]);
  await context.globalState.update(activeLocationsKey, {
    agentFilesLocations: agents, agentSkillsLocations: skills, instructionsFilesLocations: instructions,
  });
}

async function addLocation(key: string, path: string, previous: string | undefined): Promise<void> {
  const chat = vscode.workspace.getConfiguration("chat");
  const current = chat.get<Record<string, boolean>>(key, {});
  const next = { ...current };
  if (previous) delete next[previous];
  next[path] = true;
  await chat.update(key, next, vscode.ConfigurationTarget.Global);
}
