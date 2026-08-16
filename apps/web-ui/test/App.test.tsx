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
});
