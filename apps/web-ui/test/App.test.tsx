import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../src/App.js";

describe("public demo workbench", () => {
  afterEach(() => vi.restoreAllMocks());

  it("creates DEMO-123 and exposes persisted status with a manual refresh", async () => {
    const task = { taskId: "TASK-1", type: "REQUIREMENT_ANALYSIS", status: "WAITING_FOR_LOCAL_COPILOT",
      scope: { ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456" }, version: 0,
      createdAt: "2026-08-16T00:00:00Z", updatedAt: "2026-08-16T00:00:00Z" };
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(task), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([task]), { status: 200 }));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "No workflow tasks yet" })).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Create DEMO-123" }));
    expect(await screen.findByText("DEMO-123 requirement analysis")).toBeVisible();
    expect(screen.getByText("Waiting for local Copilot")).toBeVisible();
  });

  it("shows a clearly simulated internal-shaped readiness result and safe Journey report", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ employeeId: "EMP-100", displayLabel: "Fictional Scrum Master", source: "ADMIN_BINDING" }))
      .mockResolvedValueOnce(Response.json({ journeyId: "ACCOUNT_OPENING", revision: 1 }))
      .mockResolvedValueOnce(Response.json({ ticketId: "DEMO-123", principalId: "PRINCIPAL-EMP-100" }))
      .mockResolvedValueOnce(Response.json([
        { provider: "JIRA", status: "SIMULATED_PASS", observedAt: "2026-08-16T00:00:00Z", source: "deterministic-fake", safeDetail: "No enterprise call." },
      ]))
      .mockResolvedValueOnce(Response.json({ status: "CONTRACT_PASS", totalEdges: 1, provenEdges: 1, gaps: [] }))
      .mockResolvedValueOnce(new Response("<!doctype html><html><body>ACCOUNT_OPENING report</body></html>", { status: 200 }));
    render(<App />);

    await screen.findByRole("heading", { name: "No workflow tasks yet" });
    await userEvent.click(screen.getByRole("button", { name: "Run fictional readiness scenario" }));

    expect(await screen.findByText("Identity · EMP-100")).toBeVisible();
    expect(screen.getByRole("row", { name: /JIRA SIMULATED_PASS deterministic-fake/i })).toBeVisible();
    expect(screen.getByText("Journey · CONTRACT_PASS")).toBeVisible();
    expect(screen.getByText(/SIMULATED_PASS and CONTRACT_PASS do not prove/i)).toBeVisible();
    expect(screen.getByTitle("Journey readiness HTML report")).toHaveAttribute("srcdoc", expect.stringContaining("ACCOUNT_OPENING report"));
  });
});
