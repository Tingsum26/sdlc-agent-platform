#!/usr/bin/env node
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { WorkflowApiClient } from "./client.js";
import { createWorkflowMcpServer } from "./server.js";
import { logDiagnostic } from "./logging.js";

const serviceUrl = process.env["WORKFLOW_SERVICE_URL"];
if (!serviceUrl) {
  logDiagnostic("configuration_invalid", { reason: "WORKFLOW_SERVICE_URL is required" });
  process.exitCode = 2;
} else {
  try {
    const api = new WorkflowApiClient(serviceUrl, fetch, process.env["WORKFLOW_DEMO_USER"]);
    const server = createWorkflowMcpServer(api);
    await server.connect(new StdioServerTransport());
  } catch {
    logDiagnostic("startup_failed", { reason: "Check configuration" });
    process.exitCode = 2;
  }
}
