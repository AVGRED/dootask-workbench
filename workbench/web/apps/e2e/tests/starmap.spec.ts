import { expect, test } from "@playwright/test";

const STAFF = process.env.STAFF_URL ?? "http://127.0.0.1:5180";
const ADMIN = process.env.ADMIN_URL ?? "http://127.0.0.1:5181";
const API = process.env.WORKBENCH_API ?? "http://127.0.0.1:9020";
const DEMO_PASSWORD = process.env.WORKBENCH_STAFF_PASSWORD ?? "";
const ADMIN_PASSWORD = process.env.WORKBENCH_ADMIN_PASSWORD ?? "";

async function tokenFor(username: string, password: string): Promise<string> {
  const response = await fetch(`${API}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  const body = await response.json();
  return body.data.token as string;
}

async function starmapCounts(token: string): Promise<{ nodes: number; edges: number; scope: string }> {
  const response = await fetch(`${API}/api/v1/starmap`, { headers: { Authorization: `Bearer ${token}` } });
  const body = await response.json();
  return {
    nodes: body.data.nodes.length,
    edges: body.data.edges.length,
    scope: body.data.scope
  };
}

test.describe("星图", () => {
  test.skip(!DEMO_PASSWORD, "需要设置 WORKBENCH_STAFF_PASSWORD");

  test("员工端星图渲染节点与连线，悬浮出详情", async ({ page }) => {
    const token = await tokenFor(process.env.WORKBENCH_STAFF_USERNAME ?? "401004", DEMO_PASSWORD);
    const expected = await starmapCounts(token);

    await page.goto(`${STAFF}/login`);
    await page.getByPlaceholder("邮箱或账号").fill(process.env.WORKBENCH_STAFF_USERNAME ?? "401004");
    await page.getByPlaceholder("请输入密码").fill(DEMO_PASSWORD);
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page.locator(".app-header")).toBeVisible({ timeout: 15_000 });

    await page.locator(".app-header .nav-item", { hasText: "星图" }).click();
    await expect(page.locator(".starmap")).toBeVisible({ timeout: 10_000 });

    // 节点数量与接口一致，且"我"一定在图上
    await expect(page.locator(".starmap .node")).toHaveCount(expected.nodes);
    await expect(page.locator(".starmap .node.self")).toHaveCount(1);

    // 连线数量与接口一致；有连线时中点必须有关系标签
    await expect(page.locator(".starmap .edge")).toHaveCount(expected.edges);
    if (expected.edges > 0) {
      const labels = page.locator(".starmap .edge-label");
      await expect(labels.first()).toBeVisible();
      await expect(labels.first()).not.toBeEmpty();
    }

    // 悬浮星星出详情卡
    await page.locator(".starmap .node").first().hover();
    await expect(page.locator(".starmap .tooltip")).toBeVisible();
  });

  test("管理端星图是全公司视角", async ({ page }) => {
    test.skip(!ADMIN_PASSWORD, "需要设置 WORKBENCH_ADMIN_PASSWORD");

    const username = process.env.WORKBENCH_ADMIN_USERNAME ?? "401001";
    const token = await tokenFor(username, ADMIN_PASSWORD);
    const expected = await starmapCounts(token);
    expect(expected.scope).toBe("all");

    await page.goto(`${ADMIN}/login`);
    await page.getByPlaceholder("邮箱或账号").fill(username);
    await page.getByPlaceholder("请输入密码").fill(ADMIN_PASSWORD);
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page.locator(".tabs")).toBeVisible({ timeout: 15_000 });

    await page.getByRole("button", { name: /星图/ }).click();
    await expect(page.locator(".starmap")).toBeVisible({ timeout: 10_000 });
    await expect(page.locator(".starmap .node")).toHaveCount(expected.nodes);
    await expect(page.getByText(/人 · .*条协作关系/)).toBeVisible();
  });
});
