import { expect, test } from "@playwright/test";

const ADMIN = process.env.ADMIN_URL ?? "http://127.0.0.1:5181";

test.describe("管理端", () => {
  test("登录 → 用户列表 → 系统设置页", async ({ page }) => {
    await page.goto(`${ADMIN}/login`);
    await page.locator("input[type='email'], input").first().fill("demo@workbench.local");
    await page.locator("input[type='password']").first().fill("secret123");
    await page.getByRole("button", { name: "登录" }).click();

    await expect(page.getByText("用户管理")).toBeVisible();
    await expect(page.getByRole("cell", { name: "demo@workbench.local" })).toBeVisible();

    await page.getByRole("button", { name: "系统设置" }).click();
    await expect(page.getByText("系统设置")).toBeVisible();
    // 非管理员会显示错误提示；管理员显示设置表格。两者都算页面正常渲染。
    const hasAlert = await page.locator(".el-alert").isVisible().catch(() => false);
    const hasTable = await page.locator(".el-table").first().isVisible().catch(() => false);
    expect(hasAlert || hasTable).toBeTruthy();
  });
});
