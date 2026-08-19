import { expect, test } from "@playwright/test";

test("M7: fictional end-to-end SDLC completes with an audit trail and report", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Run fictional end-to-end SDLC" }).click();

  await expect(page.getByRole("list", { name: "M7 audit trail" })).toBeVisible();
  // The driver pushes `task created` and `manual E2E passed` once per SDLC stage
  // (4 stages), so the audit <ol> renders 4 <li>s per label: assert counts.
  await expect(page.getByText("epic created")).toBeVisible();
  await expect(page.getByText("task created")).toHaveCount(4);
  await expect(page.getByText("requirement analysis artifact submitted")).toBeVisible();
  await expect(page.getByText("design artifact submitted")).toBeVisible();
  await expect(page.getByText("implementation artifact submitted")).toBeVisible();
  await expect(page.getByText("generated tests artifact submitted")).toBeVisible();
  await expect(page.getByText("manual E2E passed")).toHaveCount(4);
  await expect(page.getByTitle("SDLC stage report")).toBeVisible();
  await expect(page.getByTitle("SDLC stage report")).toHaveAttribute(
    "srcdoc", expect.stringContaining("Fictional generated tests report"));
});
