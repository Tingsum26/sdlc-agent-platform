import { describe, expect, it, vi } from "vitest";
import { WorkflowClient } from "../src/api/workflowClient.js";

describe("WorkflowClient", () => {
  it("reuses ETag and correlation data without exposing a backend", async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response("[]", { status: 200, headers: { ETag: "v1" } }))
      .mockResolvedValueOnce(new Response(null, { status: 304 }));
    const client = new WorkflowClient("http://127.0.0.1:8080", fetcher, "developer-1");
    expect(await client.listTasks()).toEqual([]);
    expect(await client.listTasks()).toEqual([]);
    expect(fetcher.mock.calls[1]?.[1]?.headers).toEqual(expect.objectContaining({ "If-None-Match": "v1" }));
  });

  it("reads evidence-labelled readiness and renders Journey reports through the service", async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ employeeId: "EMP-100" }))
      .mockResolvedValueOnce(Response.json([{ provider: "JIRA", status: "SIMULATED_PASS" }]))
      .mockResolvedValueOnce(Response.json({ complete: false, provider: "JIRA" }))
      .mockResolvedValueOnce(new Response("<!doctype html><title>Journey</title>", { status: 200, headers: { "content-type": "text/html" } }));
    const client = new WorkflowClient("http://127.0.0.1:8080", fetcher, "PRINCIPAL-EMP-100");

    expect(await client.getIdentity()).toEqual({ employeeId: "EMP-100" });
    expect(await client.getIntegrationDiagnostics()).toHaveLength(1);
    expect(await client.getNextInternalValidation()).toEqual({ complete: false, provider: "JIRA" });
    expect(await client.renderJourneyReport({ schemaVersion: "1.0" })).toContain("<!doctype html>");
    expect(fetcher.mock.calls.map(([url]) => url)).toEqual([
      "http://127.0.0.1:8080/api/v1/internal-readiness/identity",
      "http://127.0.0.1:8080/api/v1/internal-readiness/integrations",
      "http://127.0.0.1:8080/api/v1/internal-readiness/next-validation",
      "http://127.0.0.1:8080/api/v1/journeys/report",
    ]);
  });
});
