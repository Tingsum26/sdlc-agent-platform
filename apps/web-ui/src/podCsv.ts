export interface PodCsvRow {
  employeeId: string;
  displayLabel: string;
  principalId: string;
  role: string;
  journeyId: string;
  active: boolean;
  effectiveFrom: string;
}

const EXPECTED_HEADER = "employeeId,displayLabel,principalId,role,journeyId,active,effectiveFrom";

export function parsePodCsv(text: string): PodCsvRow[] {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
  const [header, ...body] = lines;
  if (header !== EXPECTED_HEADER) {
    throw new Error("pod-csv-header-mismatch");
  }
  return body.map((line) => {
    const [employeeId, displayLabel, principalId, role, journeyId, active, effectiveFrom] = line.split(",");
    if (!employeeId || !displayLabel || !principalId || !role || !journeyId || !active || !effectiveFrom) {
      throw new Error("pod-csv-row-incomplete");
    }
    return { employeeId, displayLabel, principalId, role, journeyId, active: active === "true", effectiveFrom };
  });
}
