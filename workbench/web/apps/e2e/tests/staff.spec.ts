import { expect, test } from "@playwright/test";

const STAFF = process.env.STAFF_URL ?? "http://127.0.0.1:5180";
const RUN = Date.now();
const PROJECT_NAME = `PW 项目 ${RUN}`;
const TASK_NAME = `PW 任务 ${RUN}`;

test.describe("员工端", () => {
  test("登录 → 建项目 → 建任务 → 完成任务", async ({ page }) => {
    await page.goto(`${STAFF}/login`);
    await page.getByPlaceholder("邮箱").fill("demo@workbench.local");
    await page.locator("input[type='password']").fill("secret123");
    await page.getByRole("button", { name: "登录" }).click();

    await expect(page.getByText("我的项目")).toBeVisible({ timeout: 15_000 });

    await page.getByRole("button", { name: "新建项目" }).click();
    const projectDialog = page.locator(".el-dialog").filter({ hasText: "新建项目" });
    await projectDialog.locator("input").first().fill(PROJECT_NAME);
    await projectDialog.getByRole("button", { name: "保存" }).click();
    await expect(page.getByRole("cell", { name: PROJECT_NAME })).toBeVisible();

    const row = page.getByRole("row", { name: new RegExp(PROJECT_NAME) });
    await row.getByRole("button", { name: "任务" }).click();
    await expect(page.getByText("任务 · 项目")).toBeVisible();

    await page.getByRole("button", { name: "新建任务" }).click();
    const taskDialog = page.locator(".el-dialog").filter({ hasText: "新建任务" });
    await taskDialog.locator("input").first().fill(TASK_NAME);
    await taskDialog.getByRole("button", { name: "保存" }).click();
    await expect(page.getByRole("cell", { name: TASK_NAME })).toBeVisible();

    const taskRow = page.getByRole("row", { name: new RegExp(TASK_NAME) });
    await taskRow.getByRole("button", { name: "完成" }).click();
    await expect(page.getByRole("cell", { name: TASK_NAME })).toHaveCount(0);

    await page.getByRole("button", { name: "← 返回项目" }).click();
    await expect(page.getByRole("cell", { name: PROJECT_NAME })).toBeVisible();
  });
});
