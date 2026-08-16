import { expect, test } from "@playwright/test";

test("fictional Account Opening readiness remains evidence-labelled end to end", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Run fictional readiness scenario" }).click();

  await expect(page.getByText("Identity · EMP-100")).toBeVisible();
  await expect(page.getByText("Assigned DEMO-123 · PRINCIPAL-EMP-100")).toBeVisible();
  await expect(page.getByRole("row", { name: /JIRA SIMULATED_PASS deterministic-fake/i })).toBeVisible();
  await expect(page.getByRole("row", { name: /SPLUNK SIMULATED_PASS deterministic-fake/i })).toBeVisible();
  await expect(page.getByText("Journey · CONTRACT_PASS")).toBeVisible();
  await expect(page.getByText(/SIMULATED_PASS and CONTRACT_PASS do not prove/i)).toBeVisible();
  await expect(page.getByTitle("Journey readiness HTML report")).toBeVisible();
});
