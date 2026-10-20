import { expect, test } from "@playwright/test";

const STAFF = process.env.STAFF_URL ?? "http://127.0.0.1:5180";
const PASSWORD = process.env.WORKBENCH_DEMO_PASSWORD ?? "";
const RUN = Date.now();
const PROJECT_NAME = `PW 项目 ${RUN}`;
const TASK_NAME = `PW 任务 ${RUN}`;

async function login(page: import("@playwright/test").Page): Promise<void> {
  await page.goto(`${STAFF}/login`);
  await page.getByPlaceholder("邮箱或账号").fill("demo@workbench.local");
  await page.getByPlaceholder("请输入密码").fill(PASSWORD);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.locator(".app-header")).toBeVisible({ timeout: 15_000 });
}

test.describe("员工端", () => {
  test.skip(!PASSWORD, "需要设置 WORKBENCH_DEMO_PASSWORD");

  test("登录 → 建项目 → 建任务 → 完成任务 → 删除项目", async ({ page }) => {
    await login(page);

    // 统一顶栏：三个导航项
    await expect(page.locator(".app-header .nav-item")).toHaveCount(4);

    await page.locator(".app-header .nav-item", { hasText: "项目" }).click();
    await expect(page.getByRole("button", { name: "新建项目" })).toBeVisible();

    await page.getByRole("button", { name: "新建项目" }).click();
    const projectDialog = page.locator(".el-dialog").filter({ hasText: "新建项目" });
    await projectDialog.locator("input").first().fill(PROJECT_NAME);
    await projectDialog.getByRole("button", { name: "保存" }).click();
    await expect(page.locator(".project-card", { hasText: PROJECT_NAME })).toBeVisible();

    const card = page.locator(".project-card", { hasText: PROJECT_NAME }).first();
    await card.getByRole("button", { name: "任务" }).click();
    await expect(page.locator(".page-title")).toContainText("任务");

    await page.getByRole("button", { name: "新建任务" }).click();
    const taskDialog = page.locator(".el-dialog").filter({ hasText: "新建任务" });
    await taskDialog.locator("input").first().fill(TASK_NAME);
    await taskDialog.getByRole("button", { name: "保存" }).click();
    await expect(page.getByRole("cell", { name: TASK_NAME })).toBeVisible();

    const row = page.getByRole("row", { name: new RegExp(TASK_NAME) });
    await row.getByRole("button", { name: "完成" }).click();
    await expect(page.getByRole("cell", { name: TASK_NAME })).toHaveCount(0);

    // 回到项目页，删除测试项目
    await page.locator(".app-header .nav-item", { hasText: "项目" }).click();
    const cleanupCard = page.locator(".project-card", { hasText: PROJECT_NAME }).first();
    await cleanupCard.getByText("更多").click();
    await page.getByRole("menuitem", { name: "删除" }).click();
    await page.locator(".el-message-box__btns").getByRole("button", { name: "确定" }).click();
    await expect(page.locator(".project-card", { hasText: PROJECT_NAME })).toHaveCount(0);
  });

  test("明暗主题切换生效且被记住", async ({ page }) => {
    await login(page);

    const html = page.locator("html");
    await expect(html).not.toHaveClass(/dark/);
    await page.locator(".app-header .icon-btn").click();
    await expect(html).toHaveClass(/dark/);

    const darkBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(darkBg).not.toBe("rgb(255, 255, 255)");

    await page.reload();
    await expect(page.locator("html")).toHaveClass(/dark/);

    await page.locator(".app-header .icon-btn").click();
    await expect(page.locator("html")).not.toHaveClass(/dark/);
  });
});
