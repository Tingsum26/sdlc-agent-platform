import { describe, expect, it, vi } from "vitest";
import { runFictionalSdlc, type SdlcStepEvent } from "./fictionalSdlcDriver";

describe("fictional SDLC driver", () => {
  it("sequences the full path and returns an audit trail", async () => {
    const calls: string[] = [];
    const epicIds: string[] = [];
    const ticketIds: string[] = [];
    let fromTicketCount = 0;
    let repoTaskVersion = 0;
    const taskTypes = new Map<string, string>();
    const taskStatuses = new Map<string, string>();
    const taskAudits = new Map<string, Array<{ action: string; previousStatus: string; newStatus: string }>>();
    const transitionTask = (taskId: string, next: string) => {
      const previous = taskStatuses.get(taskId) ?? "MISSING";
      taskStatuses.set(taskId, next);
      taskAudits.get(taskId)?.push({ action: "TASK_TRANSITIONED", previousStatus: previous, newStatus: next });
    };
    const fetchMock = vi.fn<typeof fetch>(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      calls.push(path);
      const body = () => JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      if (path.endsWith("/epics")) {
        epicIds.push(String(body().epicId));
        return json({ epicId: body().epicId, title: "Fictional M7 epic", journeyId: "ACCOUNT_OPENING", status: "CREATED", version: 0 });
      }
      if (path.endsWith("/activate")) {
        return json({ epicId: "EPIC-M7-1", status: "ACTIVE", version: 1 });
      }
      if (path.endsWith("/tickets")) {
        if (!init?.method || init.method === "GET") return json([{ ticketId: ticketIds.at(-1), status: "E2E_VERIFIED", version: 9 }]);
        ticketIds.push(String(body().ticketId));
        return json({ ticketId: body().ticketId, epicId: "EPIC-M7", channel: "API", status: "PLANNED", version: 0 });
      }
      if (path.endsWith("/repo-tasks")) {
        if (!init?.method || init.method === "GET") return json([{ repoTaskId: "REPO-TASK-M7-1", status: "MERGED", version: 3 }]);
        return json({ repoTaskId: "REPO-TASK-M7-1", status: "PLANNED", version: 0 });
      }
      if (path.includes("/repo-tasks/") && path.endsWith("/advance")) {
        repoTaskVersion += 1;
        return json({ repoTaskId: "REPO-TASK-M7-1", status: body().target, version: repoTaskVersion });
      }
      if (path.endsWith("/advance")) {
        return json({ ticketId: "M7-API-1", status: body().target, version: 1 });
      }
      if (path.includes("/tickets/") && path.endsWith("/ci")) {
        return json({ ticket: { ticketId: "M7-API-1", status: "CI_PASSED", version: 5 }, state: "PASSED" });
      }
      if (path.endsWith("/from-ticket")) {
        fromTicketCount += 1;
        const scope = body();
        const taskId = `TASK-M7-${fromTicketCount}`;
        taskTypes.set(taskId, String(scope.type));
        taskStatuses.set(taskId, "WAITING_FOR_LOCAL_COPILOT");
        taskAudits.set(taskId, []);
        return json({
          taskId,
          type: scope.type,
          status: "WAITING_FOR_LOCAL_COPILOT",
          version: 0,
          scope: { ticketId: scope.ticketId, repositoryAlias: scope.repositoryAlias, targetCommit: scope.targetCommit },
        });
      }
      if (path.endsWith("/tasks")) {
        return json([...taskTypes.entries()].map(([taskId, type]) => ({
          taskId, type, status: taskStatuses.get(taskId),
        })));
      }
      if (path.endsWith("/resume")) return json({
        epic: { epicId: epicIds.at(-1), status: "ACTIVE" },
        tickets: [{ ticket: { ticketId: ticketIds.at(-1), status: "E2E_VERIFIED" }, openTasks: [] }],
        auditTrail: [{ action: "TICKET_TRANSITIONED", detail: "FLAG_ENABLED->E2E_VERIFIED" }],
      });
      if (path.endsWith("/audit")) {
        const taskId = path.match(/\/tasks\/([^/]+)\/audit$/)?.[1] ?? "MISSING";
        return json(taskAudits.get(taskId) ?? []);
      }
      const taskMatch = path.match(/\/tasks\/([^/]+)\/([\w-]+)$/);
      if (taskMatch) {
        const taskId = taskMatch[1];
        const action = taskMatch[2];
        if (action === "claim") {
          transitionTask(taskId, "LOCAL_COPILOT_RUNNING");
          return json({ taskId, status: "LOCAL_COPILOT_RUNNING", version: 1 });
        }
        if (action === "results") {
          transitionTask(taskId, "WAITING_FOR_USER_CONFIRMATION");
          return json({ artifactId: String(body().artifactId), taskId, type: "REQUIREMENT_REPORT", version: 1 });
        }
        if (action === "confirm") {
          transitionTask(taskId, "WAITING_FOR_APPROVAL");
          return json({ taskId, status: "WAITING_FOR_APPROVAL", version: 3 });
        }
        if (action === "ci") {
          if (taskStatuses.get(taskId) !== "WAITING_FOR_CI") return json({ title: "Workflow conflict" }, 409);
          const next = taskTypes.get(taskId) === "MANUAL_E2E" ? "WAITING_FOR_MANUAL_E2E" : "COMPLETED";
          transitionTask(taskId, next);
          return json({ taskId, status: next, version: 5 });
        }
        if (action === "manual-e2e") {
          if (taskTypes.get(taskId) !== "MANUAL_E2E" || taskStatuses.get(taskId) !== "WAITING_FOR_MANUAL_E2E") {
            return json({ title: "Workflow conflict" }, 409);
          }
          transitionTask(taskId, "COMPLETED");
          return json({ taskId, status: "COMPLETED", version: 6 });
        }
      }
      if (path.endsWith("/approvals")) {
        const approval = body();
        const taskId = String(approval.taskId);
        const type = taskTypes.get(taskId);
        const next = type === "REQUIREMENT_ANALYSIS" || type === "DESIGN" ? "COMPLETED" : "WAITING_FOR_CI";
        transitionTask(taskId, next);
        return json({ taskId, status: next, version: 4 });
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
    expect(labels).toContain("repo task created");
    // The driver labels stages specifically ("requirement analysis artifact submitted"),
    // so assert the plan's "requirement artifact submitted" intent by substring.
    expect(labels.some((label) => label.startsWith("requirement") && label.includes("artifact submitted"))).toBe(true);
    // Each stage approves its artifact ("requirement analysis approved", ...).
    expect(labels.some((label) => label.includes("approved"))).toBe(true);
    expect(labels).toContain("manual E2E passed");
    expect(labels.filter((label) => label === "manual E2E passed")).toHaveLength(1);
    expect(labels).toContain("stage terminal policy");
    expect(labels).toContain("repo task merged");
    expect(labels).toContain("ticket release evidence recorded");
    expect(calls.filter((path) => path.endsWith("/from-ticket"))).toHaveLength(6);
    const stageBodies = fetchMock.mock.calls.filter(([path]) => String(path).endsWith("/from-ticket"))
      .map(([, init]) => (JSON.parse(String(init?.body)) as { type: string }).type);
    expect(stageBodies).toEqual(["REQUIREMENT_ANALYSIS", "DESIGN", "IMPLEMENTATION", "TEST_GENERATION", "PR_REVIEW", "MANUAL_E2E"]);
    expect(calls.some((path) => path.includes("/repo-tasks/REPO-TASK-M7-1/advance"))).toBe(true);
    expect(calls.filter((path) => /\/tasks\/[^/]+\/ci$/.test(path))).toHaveLength(4);
    expect(calls.filter((path) => /\/tasks\/[^/]+\/manual-e2e$/.test(path))).toHaveLength(1);
    expect(result.auditTrail).toEqual(expect.arrayContaining([expect.objectContaining({ action: "TICKET_TRANSITIONED" })]));
    expect(result.epic.status).toBe("ACTIVE");
    expect(result.repoTask.status).toBe("MERGED");
    expect(result.ticket.status).toBe("E2E_VERIFIED");
    expect(result.tasks.every((task) => task.status === "COMPLETED")).toBe(true);
    expect(labels).toEqual(expect.arrayContaining([
      "persisted epic state", "persisted ticket state", "persisted repo task state", "persisted service audit",
    ]));

    await runFictionalSdlc(fetchMock as unknown as typeof fetch, {
      ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567",
    });
    expect(epicIds).toHaveLength(2);
    expect(new Set(epicIds).size).toBe(2);
    expect(ticketIds).toHaveLength(2);
    expect(new Set(ticketIds).size).toBe(2);
    expect(ticketIds.every((ticketId) => ticketId.startsWith("DEMO-123-M7-"))).toBe(true);
  });
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
