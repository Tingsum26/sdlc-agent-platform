import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { WorkflowApiClient } from "./client.js";
import { registerWorkflowTools } from "./tools/workflowTools.js";

export function createWorkflowMcpServer(api: WorkflowApiClient): McpServer {
  const server = new McpServer({ name: "sdlc-workflow-mcp", version: "0.1.0" });
  registerWorkflowTools(server, api);
  return server;
}
