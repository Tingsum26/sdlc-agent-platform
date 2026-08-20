import { expect, test } from "@playwright/test";

test("M7: fictional end-to-end SDLC completes with an audit trail and report", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Run fictional end-to-end SDLC" }).click();

  await expect(page.getByRole("list", { name: "M7 audit trail" })).toBeVisible();
  await expect(page.getByText("epic created")).toBeVisible();
  await expect(page.getByText("repo task created")).toBeVisible();
  await expect(page.getByRole("list", { name: "M7 audit trail" }).locator("li").filter({
    hasText: /^task created/,
  })).toHaveCount(6);
  await expect(page.getByText("requirement analysis artifact submitted")).toBeVisible();
  await expect(page.getByText("design artifact submitted")).toBeVisible();
  await expect(page.getByText("implementation artifact submitted")).toBeVisible();
  await expect(page.getByText("generated tests artifact submitted")).toBeVisible();
  await expect(page.getByText("PR review artifact submitted")).toBeVisible();
  await expect(page.getByText("manual E2E simulation artifact submitted")).toBeVisible();
  await expect(page.getByText("manual E2E passed")).toHaveCount(0);
  await expect(page.getByText("simulated manual E2E transition")).toHaveCount(1);
  await expect(page.getByText("simulation evidence boundary")).toBeVisible();
  await expect(page.getByText("SIMULATED_PASS · no QA execution or manual evidence persisted")).toBeVisible();
  await expect(page.getByText("stage terminal policy")).toBeVisible();
  await expect(page.getByText("approval-only 2 · CI-only 3 · simulated manual gate 1")).toBeVisible();
  await expect(page.getByText("simulated ticket CI transition")).toBeVisible();
  await expect(page.getByText("persisted stage types")).toBeVisible();
  await expect(page.getByText("REQUIREMENT_ANALYSIS, DESIGN, IMPLEMENTATION, TEST_GENERATION, PR_REVIEW, MANUAL_E2E")).toBeVisible();
  await expect(page.getByText("repo task merged")).toBeVisible();
  await expect(page.getByText("ticket release evidence recorded")).toBeVisible();
  await expect(page.getByText("persisted epic state")).toBeVisible();
  await expect(page.getByText("persisted ticket state")).toBeVisible();
  await expect(page.getByText("persisted repo task state")).toBeVisible();
  await expect(page.getByText("persisted service audit")).toBeVisible();
  await expect(page.getByTitle("SDLC stage report")).toBeVisible();
  await expect(page.getByTitle("SDLC stage report")).toHaveAttribute(
    "srcdoc", expect.stringContaining("SIMULATED_PASS marker only; no QA execution or manual evidence is persisted."));
  expect(await page.getByTitle("SDLC stage report").getAttribute("srcdoc")).not.toContain("QA passed");
});
