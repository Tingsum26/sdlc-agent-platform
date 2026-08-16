import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ManualE2ERunner } from "../src/reports/ManualE2ERunner.js";

describe("ManualE2ERunner", () => {
  it("does not accept PASS without traceable execution evidence", async () => {
    const user = userEvent.setup();
    const submit = vi.fn();
    render(<ManualE2ERunner caseId="E2E-1" title="Complete fictional onboarding" onSubmit={submit} />);

    await user.selectOptions(screen.getByLabelText("Result"), "PASS");
    await user.click(screen.getByRole("button", { name: "Save manual result" }));

    expect(submit).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(/actor role, execution time, build fingerprint, actual result/i);
    expect(screen.getByRole("alert")).toHaveFocus();
  });

  it("submits PASS with actor, time, build, actual result, and evidence", async () => {
    const user = userEvent.setup();
    const submit = vi.fn();
    render(<ManualE2ERunner caseId="E2E-1" title="Complete fictional onboarding" onSubmit={submit} />);

    await user.selectOptions(screen.getByLabelText("Result"), "PASS");
    await user.type(screen.getByLabelText("Actor role"), "QA");
    await user.type(screen.getByLabelText("Execution time"), "2026-08-16T08:00");
    await user.type(screen.getByLabelText("Build fingerprint"), "REPO_A@0123456");
    await user.type(screen.getByLabelText("Actual result"), "Journey completed and confirmation appeared.");
    await user.type(screen.getByLabelText("Evidence or waiver"), "Screenshot reference EVIDENCE-1");
    await user.click(screen.getByRole("button", { name: "Save manual result" }));

    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ result: "PASS", actorRole: "QA" }));
  });
});
