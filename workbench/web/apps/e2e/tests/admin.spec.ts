import { expect, test } from "@playwright/test";

const ADMIN = process.env.ADMIN_URL ?? "http://127.0.0.1:5181";
const ADMIN_USER = process.env.WORKBENCH_ADMIN_USERNAME ?? "admin";
const ADMIN_PASSWORD = process.env.WORKBENCH_ADMIN_PASSWORD ?? "";

async function login(page: import("@playwright/test").Page): Promise<void> {
  await page.goto(`${ADMIN}/login`);
  await page.getByPlaceholder("邮箱或账号").fill(ADMIN_USER);
  await page.getByPlaceholder("请输入密码").fill(ADMIN_PASSWORD);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.locator(".tabs")).toBeVisible({ timeout: 15_000 });
}

test.describe("管理端", () => {
  test.skip(!ADMIN_PASSWORD, "需要设置 WORKBENCH_ADMIN_PASSWORD");

  test("登录 → 三个 tab 可切换 → 用户管理 → 系统设置", async ({ page }) => {
    await login(page);

    await expect(page.locator(".tabs .tab")).toHaveCount(3);
    await expect(page.getByRole("button", { name: /状态墙/ })).toHaveClass(/active/);
    await expect(page.locator(".person").first()).toBeVisible();

    await page.getByRole("button", { name: /项目总览/ }).click();
    await expect(page.locator(".stat-card").first()).toContainText("项目总数");

    await page.getByRole("button", { name: /每日日报/ }).click();
    await expect(page.locator(".stat").first()).toContainText("已提交");

    await page.getByRole("button", { name: /状态墙/ }).click();
    await expect(page.locator(".chip").first()).toContainText("全部");

    // 状态筛选：切到「专注」后仍是合法渲染（人数可能为 0）
    await page.locator(".chip", { hasText: "专注" }).click();
    await expect(page.locator(".chip", { hasText: "专注" })).toHaveClass(/active/);
    await page.locator(".chip", { hasText: "全部" }).click();

    await page.getByRole("button", { name: "用户管理" }).click();
    await expect(page.locator(".page-title")).toContainText("用户管理");
    await expect(page.getByRole("cell", { name: "demo@workbench.local" })).toBeVisible();

    await page.getByRole("button", { name: "设置" }).click();
    await expect(page.locator(".page-title")).toContainText("系统设置");
    const hasAlert = await page.locator(".el-alert").first().isVisible().catch(() => false);
    const hasTable = await page.locator(".el-table").first().isVisible().catch(() => false);
    expect(hasAlert || hasTable).toBeTruthy();
  });

  test("管理端暗色切换生效", async ({ page }) => {
    await login(page);
    const html = page.locator("html");
    await expect(html).not.toHaveClass(/dark/);
    await page.locator(".right .el-button").filter({ hasText: /🌙|☀️/ }).click();
    await expect(html).toHaveClass(/dark/);
  });
});
