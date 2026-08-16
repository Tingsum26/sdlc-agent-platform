import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkflowApiClient } from "../src/client.js";
import { createWorkflowMcpServer } from "../src/server.js";

describe("internal readiness tools", () => {
  afterEach(() => vi.restoreAllMocks());

  it("marks observation tools read-only and calls identity safely", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ employeeId: "EMP-100" }), {
      status: 200, headers: { "content-type": "application/json" },
    }));
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher, "PRINCIPAL-EMP-100"));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport); await client.connect(clientTransport);

    const listed = await client.listTools();
    for (const name of ["workflow_get_identity", "workflow_get_integration_diagnostics", "workflow_get_next_internal_validation"]) {
      expect(listed.tools.find((tool) => tool.name === name)?.annotations?.readOnlyHint).toBe(true);
    }
    const result = await client.callTool({ name: "workflow_get_identity", arguments: {} });
    expect(result.content).toEqual([{ type: "text", text: JSON.stringify({ employeeId: "EMP-100" }) }]);
    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/internal-readiness/identity", expect.anything());
    await client.close(); await server.close();
  });

  it("requires explicit confirmation before Pod persistence", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport); await client.connect(clientTransport);

    const result = await client.callTool({ name: "workflow_import_pod_roster", arguments: {
      confirmed: false, journeyId: "ACCOUNT_OPENING", expectedRevision: 0, memberships: [],
    } });
    expect(result.isError).toBe(true);
    expect(fetcher).not.toHaveBeenCalled();
    await client.close(); await server.close();
  });

  it("rejects oversized Journey input before any network request", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport); await client.connect(clientTransport);

    const result = await client.callTool({ name: "workflow_analyze_journey", arguments: {
      manifest: { schemaVersion: "1.0", journeyId: "ACCOUNT_OPENING", repositories: Array(201).fill({}) },
    } });
    expect(result.isError).toBe(true);
    expect(fetcher).not.toHaveBeenCalled();
    await client.close(); await server.close();
  });
});
