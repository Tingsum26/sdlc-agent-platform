import { expect, test } from "@playwright/test";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { WorkflowApiClient } from "../apps/workflow-mcp/src/client.js";
import { createWorkflowMcpServer } from "../apps/workflow-mcp/src/server.js";

test("DEMO-123 completes the public local-Copilot vertical slice", async ({ page, request }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Create DEMO-123" }).click();
  await expect(page.getByText("DEMO-123 requirement analysis")).toBeVisible();

  const tasksResponse = await request.get("http://127.0.0.1:8080/api/v1/tasks", { headers: { "X-Demo-User": "developer-1" } });
  const tasks = await tasksResponse.json();
  const task = tasks.find((value: { scope: { ticketId: string } }) => value.scope.ticketId === "DEMO-123");
  expect(task).toBeTruthy();

  const server = createWorkflowMcpServer(new WorkflowApiClient("http://127.0.0.1:8080", fetch, "developer-1"));
  const client = new Client({ name: "public-e2e", version: "1.0.0" });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  await server.connect(serverTransport); await client.connect(clientTransport);
  await client.callTool({ name: "workflow_claim_task", arguments: { taskId: task.taskId, expectedVersion: 0, leaseMinutes: 15 } });
  await client.callTool({ name: "workflow_submit_artifact", arguments: {
    taskId: task.taskId, artifactId: "ART-DEMO-123", type: "REQUIREMENT_REPORT",
    sections: [{ key: "summary", title: "Requirement summary", body: "Fictional public requirement evidence." }],
  } });
  await client.callTool({ name: "workflow_complete_task", arguments: { taskId: task.taskId, expectedVersion: 2 } });
  await client.callTool({ name: "workflow_request_approval", arguments: {
    taskId: task.taskId, artifactId: "ART-DEMO-123", artifactVersion: 1, expectedTaskVersion: 3,
  } });
  await client.close(); await server.close();

  const headers = { "X-Demo-User": "developer-1", "Content-Type": "application/json" };
  const completedTask = await request.get(`http://127.0.0.1:8080/api/v1/tasks/${task.taskId}`, { headers });
  expect(completedTask.ok()).toBeTruthy();
  await expect(completedTask.json()).resolves.toMatchObject({
    taskId: task.taskId,
    type: "REQUIREMENT_ANALYSIS",
    status: "COMPLETED",
  });

  await page.getByRole("button", { name: "Refresh tasks" }).click();
  await expect(page.getByText("Completed")).toBeVisible();
  const audit = await request.get(`http://127.0.0.1:8080/api/v1/tasks/${task.taskId}/audit`, { headers });
  expect(audit.ok()).toBeTruthy();
  await expect(audit.json()).resolves.toEqual(expect.arrayContaining([
    expect.objectContaining({ action: "TASK_CREATED" }),
    expect.objectContaining({ action: "TASK_TRANSITIONED", newStatus: "COMPLETED" }),
  ]));
  const report = await request.get("http://127.0.0.1:8080/api/v1/reports/ART-DEMO-123/versions/1", { headers });
  expect(await report.text()).toContain("Fictional public requirement evidence.");
});
