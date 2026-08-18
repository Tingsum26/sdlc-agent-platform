import { expect, test } from "@playwright/test";

test("M4: Account Opening journey report shows evidence badges and refreshes after staleness", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Load Account Opening journey" }).click();
  await page.getByRole("button", { name: "Observe API_REPO (LIVE)" }).click();
  await expect(page.getByText(/Observed API_REPO/)).toBeVisible();
  await page.getByRole("button", { name: "Mark WEB_REPO stale" }).click();
  await expect(page.getByText(/Marked WEB_REPO stale/)).toBeVisible();

  await page.getByRole("button", { name: "Refresh journey report" }).click();
  await expect(page.getByText("API_REPO — LIVE")).toBeVisible();
  await expect(page.getByText("WEB_REPO — STALE")).toBeVisible();
  await expect(page.getByTitle("Journey readiness HTML report")).toBeVisible();
  await expect(page.getByTitle("Journey readiness HTML report")).toHaveAttribute(
    "srcdoc", expect.stringContaining("Evidence status: CONTRACT_PASS"));
});
