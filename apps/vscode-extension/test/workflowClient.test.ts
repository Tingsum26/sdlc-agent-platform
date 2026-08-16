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
});
