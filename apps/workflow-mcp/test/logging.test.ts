import { describe, expect, it, vi } from "vitest";
import { logDiagnostic } from "../src/logging.js";

describe("MCP diagnostics", () => {
  it("writes structured stderr and redacts secret-like fields", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => undefined);
    logDiagnostic("tool_failed", { correlationId: "corr-1", token: "must-not-log", status: 500 });
    const entry = JSON.parse(String(error.mock.calls[0]?.[0]));
    expect(entry).toMatchObject({ component: "workflow-mcp", event: "tool_failed", correlationId: "corr-1", status: 500 });
    expect(entry).not.toHaveProperty("token");
  });
});
