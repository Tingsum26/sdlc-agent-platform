import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { describe, expect, it } from "vitest";

function schema(name: string) {
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  addFormats(ajv);
  const path = fileURLToPath(new URL(`../schemas/${name}`, import.meta.url));
  return ajv.compile(JSON.parse(readFileSync(path, "utf8")));
}

describe("human-readable report contracts", () => {
  it("requires traceable evidence for a manual PASS", () => {
    const validate = schema("manual-e2e-case-v1.schema.json");
    const incomplete = { schemaVersion: "1.0", caseId: "E2E-1", title: "Open account", result: "PASS" };
    expect(validate(incomplete)).toBe(false);
    expect(validate({
      ...incomplete,
      actorRole: "QA",
      executedAt: "2026-08-16T08:00:00Z",
      buildFingerprint: "REPO_A@0123456",
      actualResult: "Confirmation rendered",
      evidenceOrWaiver: "EVIDENCE-1",
    })).toBe(true);
  });

  it("allows an AI-generated manual case to remain explicitly NOT_RUN", () => {
    const validate = schema("manual-e2e-case-v1.schema.json");
    expect(validate({ schemaVersion: "1.0", caseId: "E2E-2", title: "Recover after timeout", result: "NOT_RUN" })).toBe(true);
  });
});
