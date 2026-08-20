import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { describe, expect, it } from "vitest";

const schemaPath = fileURLToPath(
  new URL("../schemas/workflow-task-v1.schema.json", import.meta.url)
);

function validator() {
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  addFormats(ajv);
  return ajv.compile(JSON.parse(readFileSync(schemaPath, "utf8")));
}

const validTask = {
  schemaVersion: "1.0",
  taskId: "TASK-001",
  type: "REQUIREMENT_ANALYSIS",
  status: "WAITING_FOR_LOCAL_COPILOT",
  evidenceClassification: "REAL",
  scope: {
    ticketId: "DEMO-123",
    repositoryAlias: "REPO_A",
    targetCommit: "0123456789abcdef0123456789abcdef01234567"
  },
  version: 0,
  createdAt: "2026-08-16T00:00:00Z",
  updatedAt: "2026-08-16T00:00:00Z"
};

describe("workflow task schema", () => {
  it("accepts a versioned task waiting for local Copilot", () => {
    expect(validator()(validTask)).toBe(true);
  });

  it("accepts an old v1 task payload without evidence classification", () => {
    const { evidenceClassification: _classification, ...oldV1Task } = validTask;
    expect(validator()(oldV1Task)).toBe(true);
  });

  it("rejects an unknown workflow status", () => {
    expect(validator()({ ...validTask, status: "AI_RUNNING_IN_CLOUD" })).toBe(false);
  });

  it("rejects a task without repository and ticket scope", () => {
    const { scope: _scope, ...withoutScope } = validTask;
    expect(validator()(withoutScope)).toBe(false);
  });

  it.each(["token", "password", "cookie"])("rejects a secret-like %s field", (field) => {
    expect(validator()({ ...validTask, [field]: "must-not-be-stored" })).toBe(false);
  });
});
