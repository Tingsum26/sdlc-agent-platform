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

const membership = {
  membershipId: "MEM-001",
  employeeId: "EMP-100",
  principalId: "PRINCIPAL-100",
  displayLabel: "Fictional Scrum Master",
  role: "SCRUM_MASTER",
  journeyId: "ACCOUNT_OPENING_DEMO",
  active: true,
  effectiveFrom: "2026-08-16",
  aliases: ["fictional-sm"],
};

const completeJourney = {
  schemaVersion: "1.0",
  journeyId: "ACCOUNT_OPENING_DEMO",
  domainId: "CUSTOMER_ONBOARDING_DEMO",
  version: 1,
  repositories: [
    { alias: "REPO_A", role: "API", ref: "0123456789abcdef0123456789abcdef01234567" },
    { alias: "REPO_WEB", role: "WEB", ref: "1123456789abcdef0123456789abcdef01234567" },
    { alias: "REPO_IOS", role: "IOS", ref: "2123456789abcdef0123456789abcdef01234567" },
    { alias: "REPO_ANDROID", role: "ANDROID", ref: "3123456789abcdef0123456789abcdef01234567" },
  ],
  screens: [{ screenId: "SCREEN_START", client: "WEB", repositoryAlias: "REPO_WEB" }],
  httpEdges: [{
    edgeId: "EDGE-001",
    caller: "SCREEN_START",
    apiRepositoryAlias: "REPO_A",
    method: "POST",
    normalizedPath: "/demo/applications",
    requestSchemaRef: "schema://demo/application-request-v1",
    responseSchemaRef: "schema://demo/application-response-v1",
    commonHeaderRule: "X-DEMO-APP-VERSION",
    authenticationClass: "CUSTOMER_SESSION",
    compatibility: "BACKWARD_COMPATIBLE",
    provenance: { source: "REPO_A", ref: "0123456789abcdef0123456789abcdef01234567", evidenceId: "EVIDENCE-EDGE-001" },
  }],
  releasePolicy: { webApiFirst: true, nativeReleaseTrain: "DEMO_MONTHLY", compatibilityWindowDays: 90, rollbackRule: "Disable demo flag" },
  featureFlag: { required: true, provider: "AWS_DEMO", ownerRole: "TECH_LEAD" },
  e2eOwners: [{ scenario: "APPLICATION_SUBMISSION", ownerRole: "QA" }],
};

describe("internal-shaped schemas", () => {
  it("accepts only the four evidence classifications", () => {
    const validate = schema("evidence-status-v1.schema.json");
    expect(validate({ schemaVersion: "1.0", status: "CONTRACT_PASS" })).toBe(true);
    expect(validate({ schemaVersion: "1.0", status: "REAL_PASS" })).toBe(false);
  });

  it("supports an administrator-bound principal without GitHub and rejects raw email", () => {
    const validate = schema("enterprise-principal-v1.schema.json");
    const principal = {
      schemaVersion: "1.0",
      principalId: "PRINCIPAL-100",
      employeeId: "EMP-100",
      displayLabel: "Fictional Scrum Master",
      maskedEmail: "f***@example.invalid",
      source: "ADMIN_BINDING",
    };
    expect(validate(principal)).toBe(true);
    expect(validate({ ...principal, maskedEmail: "fictional.sm@example.invalid" })).toBe(false);
  });

  it("requires a versioned roster and rejects duplicate rows", () => {
    const validate = schema("pod-roster-v1.schema.json");
    expect(validate({ schemaVersion: "1.0", journeyId: "ACCOUNT_OPENING_DEMO", revision: 1, memberships: [membership] })).toBe(true);
    expect(validate({ schemaVersion: "1.0", journeyId: "ACCOUNT_OPENING_DEMO", revision: 1, memberships: [membership, membership] })).toBe(false);
  });

  it("requires safe integration diagnostic provenance", () => {
    const validate = schema("integration-diagnostic-v1.schema.json");
    expect(validate({ schemaVersion: "1.0", provider: "JIRA", status: "SIMULATED_PASS", observedAt: "2026-08-16T00:00:00Z", source: "deterministic-fake", safeDetail: "Ticket mapping accepted" })).toBe(true);
    expect(validate({ schemaVersion: "1.0", provider: "JIRA", status: "PASS", observedAt: "2026-08-16T00:00:00Z", source: "fake", safeDetail: "ok", token: "forbidden" })).toBe(false);
  });

  it("requires Journey provenance and compatibility", () => {
    const validate = schema("journey-manifest-v1.schema.json");
    expect(validate(completeJourney)).toBe(true);
    const edge = completeJourney.httpEdges[0]!;
    expect(validate({ ...completeJourney, httpEdges: [{ ...edge, provenance: undefined }] })).toBe(false);
    expect(validate({ ...completeJourney, httpEdges: [{ ...edge, compatibility: undefined }] })).toBe(false);
  });
});
