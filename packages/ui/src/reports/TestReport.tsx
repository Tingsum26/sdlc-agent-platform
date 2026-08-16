import { ReportFrame } from "../ReportFrame.js";

export interface TestEvidence { name: string; status: "PASSED" | "FAILED" | "NOT_RUN"; evidence: string }

export function TestReport({ automated, generatedManualCases }: {
  automated: TestEvidence[]; generatedManualCases: TestEvidence[];
}) {
  return <ReportFrame title="Test report">
    <section><h2>Executed automated evidence</h2>{automated.map((item) =>
      <p key={item.name}><strong>{item.status}</strong> {item.name}: {item.evidence}</p>)}</section>
    <section><h2>AI-generated manual cases — not executed</h2>{generatedManualCases.map((item) =>
      <p key={item.name}><strong>NOT RUN</strong> {item.name}: {item.evidence}</p>)}</section>
  </ReportFrame>;
}
