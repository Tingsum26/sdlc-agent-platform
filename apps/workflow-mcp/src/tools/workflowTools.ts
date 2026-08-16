import { randomUUID } from "node:crypto";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { WorkflowApiError, type WorkflowApiClient } from "../client.js";
import { logDiagnostic } from "../logging.js";

const textResult = (value: unknown) => ({
  content: [{ type: "text" as const, text: JSON.stringify(value) }],
});

const safe = async (tool: string, operation: (correlationId: string) => Promise<unknown>) => {
  const correlationId = randomUUID();
  try {
    return textResult(await operation(correlationId));
  } catch (error) {
    logDiagnostic("tool_failed", {
      tool,
      correlationId,
      status: error instanceof WorkflowApiError ? error.status : undefined,
    });
    const message = error instanceof WorkflowApiError
      ? `${error.message} (status ${error.status}, correlation ${error.correlationId})`
      : `Workflow tool failed (correlation ${correlationId})`;
    return { isError: true, content: [{ type: "text" as const, text: message }] };
  }
};

export function registerWorkflowTools(server: McpServer, api: WorkflowApiClient): void {
  server.registerTool("workflow_list_my_tasks", {
    description: "List persisted workflow tasks visible to the current user.",
    inputSchema: z.object({}),
    annotations: { readOnlyHint: true },
  }, (_args, extra) => safe("workflow_list_my_tasks", (correlationId) => api.listTasks(correlationId, extra.signal)));

  server.registerTool("workflow_get_task_context", {
    description: "Read the persisted task state before starting or resuming local Copilot work.",
    inputSchema: z.object({ taskId: z.string().min(1) }),
    annotations: { readOnlyHint: true },
  }, ({ taskId }, extra) => safe("workflow_get_task_context", (correlationId) => api.getTaskContext(taskId, correlationId, extra.signal)));

  server.registerTool("workflow_claim_task", {
    description: "Claim a task with a bounded lease before local Copilot reasoning begins.",
    inputSchema: z.object({
      taskId: z.string().min(1),
      expectedVersion: z.number().int().nonnegative(),
      leaseMinutes: z.number().int().min(1).max(120).default(30),
    }),
  }, ({ taskId, expectedVersion, leaseMinutes }, extra) => safe("workflow_claim_task", (correlationId) =>
    api.claimTask(taskId, expectedVersion, leaseMinutes, correlationId, extra.signal)));

  server.registerTool("workflow_submit_artifact", {
    description: "Persist a structured, human-readable workflow artifact produced by local Copilot.",
    inputSchema: z.object({
      taskId: z.string().min(1),
      artifactId: z.string().min(1),
      type: z.enum(["REQUIREMENT_REPORT", "DESIGN_REPORT", "TEST_REPORT", "MANUAL_E2E_REPORT", "PR_REVIEW_REPORT", "DELIVERY_REPORT"]),
      sections: z.array(z.object({ key: z.string(), title: z.string(), body: z.string() })).min(1),
      contentHash: z.string().optional(),
    }),
  }, ({ taskId, ...artifact }, extra) => safe("workflow_submit_artifact", (correlationId) =>
    api.submitArtifact(taskId, artifact, correlationId, extra.signal)));

  server.registerTool("workflow_request_approval", {
    description: "Record a human approval for an exact artifact and task version.",
    inputSchema: z.object({
      taskId: z.string().min(1),
      artifactId: z.string().min(1),
      artifactVersion: z.number().int().positive(),
      expectedTaskVersion: z.number().int().nonnegative(),
    }),
  }, (approval, extra) => safe("workflow_request_approval", (correlationId) => api.requestApproval(approval, correlationId, extra.signal)));

  server.registerTool("workflow_complete_task", {
    description: "Confirm the current local result and advance it to its next persisted gate.",
    inputSchema: z.object({
      taskId: z.string().min(1),
      expectedVersion: z.number().int().nonnegative(),
    }),
  }, ({ taskId, expectedVersion }, extra) => safe("workflow_complete_task", (correlationId) =>
    api.completeTask(taskId, expectedVersion, correlationId, extra.signal)));
}
