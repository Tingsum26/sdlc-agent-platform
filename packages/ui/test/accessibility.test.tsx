import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ApprovalPanel } from "../src/ApprovalPanel.js";
import { ErrorState } from "../src/ErrorState.js";
import { ReportFrame } from "../src/ReportFrame.js";
import { TaskList } from "../src/TaskList.js";
import { TaskStatusBadge } from "../src/TaskStatusBadge.js";

describe("accessible workflow components", () => {
  it("communicates status with text and an accessible icon rather than color alone", () => {
    render(<TaskStatusBadge status="WAITING_FOR_APPROVAL" />);
    expect(screen.getByText("Waiting for approval")).toBeVisible();
    expect(screen.getByRole("status", { name: "Waiting for approval" })).toBeVisible();
  });

  it("keeps task actions in document order and labels stale state", async () => {
    const user = userEvent.setup();
    const open = vi.fn();
    render(<TaskList tasks={[{
      taskId: "TASK-1", title: "Clarify DEMO-123", repositoryAlias: "REPO_A",
      status: "BLOCKED", updatedAt: "2026-08-16T00:00:00Z", stale: true,
    }]} onOpen={open} />);
    const button = screen.getByRole("button", { name: /Open Clarify DEMO-123/i });
    await user.tab();
    expect(button).toHaveFocus();
    expect(screen.getByText("Possibly stale")).toBeVisible();
    await user.keyboard("{Enter}");
    expect(open).toHaveBeenCalledWith("TASK-1");
  });

  it("requires an explicit human confirmation before approval", async () => {
    const user = userEvent.setup();
    const approve = vi.fn();
    render(<ApprovalPanel artifactId="ART-1" artifactVersion={3} taskVersion={7} onApprove={approve} />);
    const button = screen.getByRole("button", { name: "Approve version 3" });
    expect(button).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: /I reviewed artifact ART-1 version 3/i }));
    await user.click(button);
    expect(approve).toHaveBeenCalledWith({ artifactId: "ART-1", artifactVersion: 3, taskVersion: 7 });
  });

  it("renders long report text as text and provides graph alternatives", () => {
    render(<ReportFrame title="Requirement report" graphDescription="API A calls API B">
      {"<script>alert('unsafe')</script>".repeat(200)}
    </ReportFrame>);
    expect(screen.getByRole("main", { name: "Requirement report" })).toHaveTextContent("<script>");
    expect(document.querySelector("script")).toBeNull();
    expect(screen.getByText("API A calls API B")).toBeVisible();
  });

  it("announces retryable errors without leaking details", () => {
    render(<ErrorState title="Workflow unavailable" correlationId="corr-123" onRetry={() => undefined} />);
    expect(screen.getByRole("alert")).toHaveTextContent("corr-123");
    expect(screen.getByRole("button", { name: "Retry" })).toBeVisible();
  });

  it("defines theme fallbacks, visible focus, reduced motion, and responsive content", () => {
    const css = readFileSync(resolve(import.meta.dirname, "../src/tokens.css"), "utf8");
    expect(css).toMatch(/--sdlc-bg:\s*var\(--vscode-editor-background,/);
    expect(css).toMatch(/:focus-visible/);
    expect(css).toMatch(/prefers-reduced-motion/);
    expect(css).toMatch(/overflow-wrap:\s*anywhere/);
  });
});
