import { expect, test } from "@playwright/test";

const MOBILE = process.env.MOBILE_URL ?? "http://127.0.0.1:5182";

test.describe("移动端", () => {
  test("登录 → 项目列表 → 进入任务页", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${MOBILE}/login`);

    await page.locator("input").nth(0).fill("demo@workbench.local");
    await page.locator("input[type='password']").fill("secret123");
    await page.getByRole("button", { name: "登录" }).click();

    await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
    const firstCard = page.locator(".card").first();
    await expect(firstCard).toBeVisible();
    await firstCard.click();

    await expect(page.getByRole("button", { name: "← 返回项目" })).toBeVisible();
  });
});
