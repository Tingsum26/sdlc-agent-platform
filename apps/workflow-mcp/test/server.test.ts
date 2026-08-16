import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkflowApiClient, WorkflowApiError } from "../src/client.js";
import { createWorkflowMcpServer } from "../src/server.js";

describe("workflow MCP", () => {
  afterEach(() => vi.restoreAllMocks());

  it("discovers the bounded workflow and internal-readiness tools", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    await client.connect(clientTransport);

    const result = await client.listTools();
    expect(result.tools.map((tool) => tool.name).sort()).toEqual([
      "workflow_analyze_journey",
      "workflow_claim_task",
      "workflow_complete_task",
      "workflow_get_identity",
      "workflow_get_integration_diagnostics",
      "workflow_get_next_internal_validation",
      "workflow_get_task_context",
      "workflow_import_pod_roster",
      "workflow_list_my_tasks",
      "workflow_request_approval",
      "workflow_submit_artifact",
      "workflow_validate_pod_roster",
    ]);
    await client.close();
    await server.close();
  });

  it("propagates correlation IDs and local demo identity", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response("[]", {
      status: 200,
      headers: { "content-type": "application/json" },
    }));
    const api = new WorkflowApiClient("http://127.0.0.1:8080", fetcher, "developer-1");

    await api.listTasks("corr-123", new AbortController().signal);

    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/tasks", expect.objectContaining({
      headers: expect.objectContaining({
        "X-Correlation-ID": "corr-123",
        "X-Demo-User": "developer-1",
      }),
    }));
  });

  it("maps failures safely and redacts secrets", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      detail: "database password=secret-value",
    }), { status: 500, headers: { "content-type": "application/json" } }));
    const api = new WorkflowApiClient("http://127.0.0.1:8080", fetcher);

    await expect(api.listTasks("corr-safe", new AbortController().signal))
      .rejects.toEqual(new WorkflowApiError(500, "Workflow Service request failed", "corr-safe"));
  });

  it("passes cancellation to fetch", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation((_url, init) =>
      new Promise((_resolve, reject) => init?.signal?.addEventListener("abort", () => reject(init.signal?.reason))));
    const api = new WorkflowApiClient("http://127.0.0.1:8080", fetcher);
    const controller = new AbortController();
    const pending = api.listTasks("corr-cancel", controller.signal);
    controller.abort(new Error("cancelled"));

    await expect(pending).rejects.toThrow("cancelled");
  });
});
