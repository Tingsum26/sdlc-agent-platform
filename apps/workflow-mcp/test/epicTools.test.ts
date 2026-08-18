import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkflowApiClient } from "../src/client.js";
import { createWorkflowMcpServer } from "../src/server.js";

describe("epic MCP tools", () => {
  afterEach(() => vi.restoreAllMocks());

  it("creates an epic through the tool surface", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      epicId: "EPIC-M2-1", title: "Fictional epic", journeyId: "ACCOUNT_OPENING",
      status: "CREATED", version: 0, createdAt: "2026-08-18T00:00:00Z", updatedAt: "2026-08-18T00:00:00Z",
    }), { status: 201, headers: { "content-type": "application/json" } }));
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    await client.connect(clientTransport);

    const result = await client.callTool({
      name: "workflow_epic_create",
      arguments: { epicId: "EPIC-M2-1", title: "Fictional epic", journeyId: "ACCOUNT_OPENING" },
    });

    expect(result.isError).toBeUndefined();
    expect(JSON.parse(String((result.content as Array<{ text: string }>)[0].text)).epicId).toBe("EPIC-M2-1");
    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/epics",
      expect.objectContaining({ method: "POST" }));
    await client.close();
    await server.close();
  });

  it("resumes an epic through the tool surface", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      epic: { epicId: "EPIC-M2-1" },
    }), { status: 200, headers: { "content-type": "application/json" } }));
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    await client.connect(clientTransport);

    const result = await client.callTool({
      name: "workflow_epic_resume",
      arguments: { epicId: "EPIC-M2-1" },
    });

    expect(result.isError).toBeUndefined();
    expect(fetcher).toHaveBeenCalledWith("http://127.0.0.1:8080/api/v1/epics/EPIC-M2-1/resume",
      expect.objectContaining({ method: "GET" }));
    await client.close();
    await server.close();
  });

  it("rejects an invalid channel", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetcher));
    const client = new Client({ name: "test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    await client.connect(clientTransport);

    const result = await client.callTool({
      name: "workflow_epic_attach_ticket",
      arguments: { epicId: "EPIC-M2-1", ticketId: "M2-API-1", channel: "DESKTOP" },
    });

    expect(result.isError).toBe(true);
    expect(fetcher).not.toHaveBeenCalled();
    await client.close();
    await server.close();
  });
});
