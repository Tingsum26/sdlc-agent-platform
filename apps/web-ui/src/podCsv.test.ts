import { describe, expect, it } from "vitest";
import { parsePodCsv } from "./podCsv";

describe("parsePodCsv", () => {
  it("parses a valid roster", () => {
    const rows = parsePodCsv([
      "employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom",
      "EMP-201,Fictional API Developer,PRINCIPAL-EMP-201,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01",
    ].join("\n"));
    expect(rows).toHaveLength(1);
    expect(rows[0]).toEqual({
      employeeId: "EMP-201",
      displayLabel: "Fictional API Developer",
      principalId: "PRINCIPAL-EMP-201",
      role: "DEVELOPER",
      journeyId: "ACCOUNT_OPENING",
      active: true,
      effectiveFrom: "2026-01-01",
    });
  });

  it("rejects a mismatched header", () => {
    expect(() => parsePodCsv("a,b\n1,2")).toThrow("pod-csv-header-mismatch");
  });

  it("rejects an incomplete row", () => {
    expect(() => parsePodCsv([
      "employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom",
      "EMP-201,,PRINCIPAL-EMP-201,DEVELOPER,ACCOUNT_OPENING,true,2026-01-01",
    ].join("\n"))).toThrow("pod-csv-row-incomplete");
  });
});
