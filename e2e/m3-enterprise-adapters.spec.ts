import { expect, test } from "@playwright/test";

test("M3: Jira comment draft-publish and simulated Jenkins CI reach the ticket view", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Run fictional end-to-end SDLC" }).click();
  const m7AuditTrail = page.getByRole("list", { name: "M7 audit trail" });
  await expect(m7AuditTrail).toBeVisible();
  await expect(m7AuditTrail.getByText("simulated release-state path recorded")).toBeVisible();

  await page.getByRole("button", { name: "Create EPIC-M2-1" }).click();
  await page.getByRole("button", { name: "Activate epic" }).click();
  await page.getByRole("button", { name: "Attach four channel tickets" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toBeVisible();

  const draftJiraComment = page.getByRole("button", { name: "Draft Jira comment from approved M7 artifact" });
  await expect(draftJiraComment).toBeEnabled();
  await draftJiraComment.click();
  await expect(page.getByText(/JIRA-PROJ-.*· REQ-APPROVED · JIRA_ARTIFACT_SYNC_PENDING/)).toBeVisible();
  await page.getByRole("button", { name: "Confirm publish Jira comment" }).click();
  await expect(page.getByText(/JIRA-PROJ-.*· REQ-APPROVED · PUBLISHED · attempts 1/)).toBeVisible();

  await page.getByRole("button", { name: "Advance M2-API-1 to PR_OPEN" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toContainText("PR_OPEN");
  await page.getByRole("button", { name: "Record Jenkins CI for M2-API-1" }).click();
  await expect(page.getByRole("row", { name: /M2-API-1/ })).toContainText("CI_PASSED");
  await expect(page.getByText(/M2-API-1 · CI_PASSED · https:\/\/example.invalid/)).toBeVisible();
});
