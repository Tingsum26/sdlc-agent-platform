#!/usr/bin/env node
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { WorkflowApiClient } from "./client.js";
import { createWorkflowMcpServer } from "./server.js";

const serviceUrl = process.env["WORKFLOW_SERVICE_URL"];
if (!serviceUrl) {
  console.error("WORKFLOW_SERVICE_URL is required");
  process.exitCode = 2;
} else {
  try {
    const api = new WorkflowApiClient(serviceUrl, fetch, process.env["WORKFLOW_DEMO_USER"]);
    const server = createWorkflowMcpServer(api);
    await server.connect(new StdioServerTransport());
  } catch {
    console.error("Workflow MCP failed to start; check configuration");
    process.exitCode = 2;
  }
}
