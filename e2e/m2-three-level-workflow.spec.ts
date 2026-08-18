import { expect, test } from "@playwright/test";

test("M2: three-level workflow with change approval, dependency gate, and skip attestation", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Create EPIC-M2-1" }).click();
  await expect(page.getByText("Epic EPIC-M2-1 · Fictional M2 epic · CREATED")).toBeVisible();
  await page.getByRole("button", { name: "Activate epic" }).click();
  await expect(page.getByText(/Epic EPIC-M2-1 · Fictional M2 epic · ACTIVE/)).toBeVisible();

  await page.getByRole("button", { name: "Attach four channel tickets" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /M2-AND-1/ })).toBeVisible();

  await page.getByRole("button", { name: "Add repo task to M2-API-1" }).click();
  await expect(page.getByText(/REPO-TASK-.*· PLANNED/)).toBeVisible();

  await page.getByRole("button", { name: "Add dependency M2-API-1 → M2-WEB-1" }).click();
  await expect(page.getByText("M2-API-1 → M2-WEB-1 · BLOCKING")).toBeVisible();

  await page.getByRole("button", { name: "Advance M2-WEB-1 to CI_PASSED" }).click();
  await expect(page.getByRole("row", { name: /M2-WEB-1/ })).toContainText("CI_PASSED");

  await page.getByRole("button", { name: "Try merge M2-WEB-1" }).click();
  await expect(page.getByText("MERGE_BLOCKED_BY_DEPENDENCY")).toBeVisible();

  await page.getByRole("button", { name: "Resolve dependency" }).click();
  await expect(page.getByText(/RESOLVED DEP-/)).toBeVisible();

  await page.getByRole("button", { name: "Create emergency change request" }).click();
  await expect(page.getByText(/Change request CR-.*· DRAFT/)).toBeVisible();
  await page.getByRole("button", { name: "Approve change as Business Owner" }).click();
  await page.getByRole("button", { name: "Approve change as Technical Owner" }).click();
  await expect(page.getByText(/Change request CR-.*· APPROVED/)).toBeVisible();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toContainText("PENDING_CHANGE_CONFIRMATION");

  await page.getByRole("button", { name: "Acknowledge change on M2-API-1" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).not.toContainText("PENDING_CHANGE_CONFIRMATION");

  await page.getByRole("button", { name: "Create DEMO-123" }).click();
  await page.getByRole("button", { name: "Skip first DEMO-123 task with attestation" }).click();
  await expect(page.getByText(/SKIPPED TASK-.*· REQUIREMENT_ANALYSIS · Fictional fast-track/)).toBeVisible();

  await page.getByRole("button", { name: "Show resume context" }).click();
  await expect(page.getByText("Resume context · ACTIVE")).toBeVisible();
  await expect(page.getByText(/M2-API-1 · PLANNED → start requirement analysis/)).toBeVisible();
  await expect(page.getByText(/Audit trail: EPIC_CREATED/)).toBeVisible();
});
