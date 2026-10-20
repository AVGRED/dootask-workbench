import { expect, test } from "@playwright/test";

const MOBILE = process.env.MOBILE_URL ?? "http://127.0.0.1:5182";
const PASSWORD = process.env.WORKBENCH_DEMO_PASSWORD ?? "";

test.describe("移动端", () => {
  test.skip(!PASSWORD, "需要设置 WORKBENCH_DEMO_PASSWORD");

  test("登录 → 项目列表 → 进入任务页", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${MOBILE}/login`);

    await page.locator("input").nth(0).fill("demo@workbench.local");
    await page.locator("input[type='password']").fill(PASSWORD);
    await page.getByRole("button", { name: "登录" }).click();

    await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
    await expect(page.locator(".tabbar")).toBeVisible();

    await page.locator(".tab", { hasText: "项目" }).click();
    const firstCard = page.locator(".card").first();
    await expect(firstCard).toBeVisible();
    await firstCard.click();

    await expect(page.getByRole("button", { name: "← 返回项目" })).toBeVisible();
  });

  test("我的页可切换明暗", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${MOBILE}/login`);
    await page.locator("input").nth(0).fill("demo@workbench.local");
    await page.locator("input[type='password']").fill(PASSWORD);
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page.locator(".tabbar")).toBeVisible({ timeout: 15_000 });

    await page.locator(".tab", { hasText: "我的" }).click();
    await expect(page.getByText("外观")).toBeVisible();

    const html = page.locator("html");
    await expect(html).not.toHaveClass(/dark/);
    await page.getByRole("button", { name: /暗色/ }).click();
    await expect(html).toHaveClass(/dark/);

    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bg).not.toBe("rgb(255, 255, 255)");
  });
});
