import { expect, test } from "@playwright/test";

test("M1: fictitious Pod CSV import lists members and assigns a not-onboarded developer", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Import fictitious Pod roster (CSV)" }).click();

  await expect(page.getByRole("row", { name: /EMP-201/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /EMP-301/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /EMP-100/ })).toContainText("ONBOARDED");
  await expect(page.getByRole("row", { name: /EMP-201/ })).toContainText("NOT_ONBOARDED");

  await page.getByRole("button", { name: "Assign DEMO-123 to first active DEVELOPER" }).click();
  await expect(page.getByText("Assigned DEMO-123 · PRINCIPAL-EMP-201")).toBeVisible();
  await expect(page.getByText(/ASSIGNEE_NOT_ONBOARDED/)).toBeVisible();
});
