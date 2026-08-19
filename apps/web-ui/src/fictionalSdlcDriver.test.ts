import { describe, expect, it, vi } from "vitest";
import { runFictionalSdlc, type SdlcStepEvent } from "./fictionalSdlcDriver";

describe("fictional SDLC driver", () => {
  it("sequences the full path and returns an audit trail", async () => {
    const calls: string[] = [];
    let fromTicketCount = 0;
    const fetchMock = vi.fn<typeof fetch>(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      calls.push(path);
      const body = () => JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      if (path.endsWith("/epics")) {
        return json({ epicId: "EPIC-M7-1", title: "Fictional M7 epic", journeyId: "ACCOUNT_OPENING", status: "CREATED", version: 0 });
      }
      if (path.endsWith("/activate")) {
        return json({ epicId: "EPIC-M7-1", status: "ACTIVE", version: 1 });
      }
      if (path.endsWith("/tickets")) {
        return json({ ticketId: "M7-API-1", epicId: "EPIC-M7-1", channel: "API", status: "PLANNED", version: 0 });
      }
      if (path.endsWith("/from-ticket")) {
        // Each SDLC stage creates its own single-stage REQUIREMENT_ANALYSIS task with a
        // distinct targetCommit (idempotency key ticket:<ticketId>:<targetCommit>).
        fromTicketCount += 1;
        const scope = body();
        return json({
          taskId: `TASK-M7-${fromTicketCount}`,
          type: "REQUIREMENT_ANALYSIS",
          status: "WAITING_FOR_LOCAL_COPILOT",
          version: 0,
          scope: { ticketId: scope.ticketId, repositoryAlias: scope.repositoryAlias, targetCommit: scope.targetCommit },
        });
      }
      const taskMatch = path.match(/\/tasks\/([^/]+)\/([\w-]+)$/);
      if (taskMatch) {
        const taskId = taskMatch[1];
        const action = taskMatch[2];
        if (action === "claim") return json({ taskId, status: "LOCAL_COPILOT_RUNNING", version: 1 });
        if (action === "results") return json({ artifactId: String(body().artifactId), taskId, type: "REQUIREMENT_REPORT", version: 1 });
        if (action === "confirm") return json({ taskId, status: "WAITING_FOR_APPROVAL", version: 2 });
        if (action === "ci") return json({ taskId, status: "WAITING_FOR_MANUAL_E2E", version: 4 });
        if (action === "manual-e2e") return json({ taskId, status: "COMPLETED", version: 5 });
      }
      if (path.endsWith("/approvals")) {
        const approval = body();
        return json({ taskId: String(approval.taskId), status: "WAITING_FOR_CI", version: 3 });
      }
      return json({});
    });

    const result = await runFictionalSdlc(fetchMock as unknown as typeof fetch, {
      ticketId: "DEMO-123",
      repositoryAlias: "REPO_A",
      targetCommit: "0123456789abcdef0123456789abcdef01234567",
    });

    expect(result.steps.length).toBeGreaterThanOrEqual(7);
    const labels = result.steps.map((step: SdlcStepEvent) => step.label);
    expect(labels).toContain("epic created");
    // The driver labels stages specifically ("requirement analysis artifact submitted"),
    // so assert the plan's "requirement artifact submitted" intent by substring.
    expect(labels.some((label) => label.startsWith("requirement") && label.includes("artifact submitted"))).toBe(true);
    // Each stage approves its artifact ("requirement analysis approved", ...).
    expect(labels.some((label) => label.includes("approved"))).toBe(true);
    expect(labels).toContain("manual E2E passed");
    expect(result.auditTrail.length).toBeGreaterThan(0);
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}
