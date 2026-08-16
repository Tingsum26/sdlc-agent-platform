import { existsSync, readFileSync, statSync } from "node:fs";
import { resolve, sep } from "node:path";

export interface BundleManifest {
  schemaVersion: "1.0";
  bundleVersion: string;
  agents: string[];
  skills: string[];
  instructions: string[];
  policies?: string[];
  schemas?: string[];
  mcpCatalog?: string;
}

export function loadAndValidateBundle(root: string, manifestPath: string): BundleManifest {
  const safeRoot = resolve(root);
  const manifestFile = safeResolve(safeRoot, manifestPath);
  const raw = readFileSync(manifestFile, "utf8");
  if (/"(?:token|password|cookie|secret|authorization)"\s*:/i.test(raw)) {
    throw new Error("Bundle manifest contains a secret-like field");
  }
  const manifest = JSON.parse(raw) as BundleManifest;
  if (manifest.schemaVersion !== "1.0" || !/^\d+\.\d+\.\d+$/.test(manifest.bundleVersion)) {
    throw new Error("Unsupported bundle manifest version");
  }
  for (const path of [...requiredArray(manifest.agents, "agents"), ...requiredArray(manifest.skills, "skills"),
    ...requiredArray(manifest.instructions, "instructions"), ...(manifest.policies ?? []), ...(manifest.schemas ?? []),
    ...(manifest.mcpCatalog ? [manifest.mcpCatalog] : [])]) {
    const target = safeResolve(safeRoot, path);
    if (!existsSync(target) || (!statSync(target).isFile() && !statSync(target).isDirectory())) {
      throw new Error(`Bundle entry does not exist: ${path}`);
    }
  }
  return manifest;
}

export function safeResolve(root: string, relativePath: string): string {
  if (!relativePath || /^([a-zA-Z]:|[\\/])/.test(relativePath)) throw new Error("Unsafe absolute bundle path");
  const target = resolve(root, relativePath);
  if (target !== root && !target.startsWith(`${root}${sep}`)) throw new Error("Unsafe bundle path traversal");
  return target;
}

function requiredArray(value: unknown, name: string): string[] {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string")) throw new Error(`Invalid ${name} inventory`);
  return value;
}
