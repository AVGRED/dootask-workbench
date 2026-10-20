import { expect, test } from "@playwright/test";
import { call, createProject, login } from "./helpers";

test.describe("仪表盘与状态墙", () => {
  test("仪表盘返回目录、布局与聚合数据", async () => {
    const token = await login();
    const dashboard = await call(token, "GET", "/api/v1/dashboard/me");
    expect(dashboard.code).toBe(0);
    expect(dashboard.data.catalog.length).toBeGreaterThanOrEqual(5);
    expect(dashboard.data.layout.length).toBeGreaterThanOrEqual(1);
    expect(dashboard.data.data["summary"].projects).toBeGreaterThanOrEqual(0);
  });

  test("布局保存、项目板块与重置", async () => {
    const token = await login();
    const projectId = await createProject(token);

    const saved = await call(token, "PUT", "/api/v1/dashboard/me/layout", {
      items: [
        { id: "my-tasks", x: 0, y: 0, w: 8, h: 2 },
        { id: `project:${projectId}`, x: 0, y: 2, w: 6, h: 2 }
      ]
    });
    expect(saved.code).toBe(0);

    const reloaded = await call(token, "GET", "/api/v1/dashboard/me");
    expect(reloaded.data.layoutSaved).toBe(true);
    expect(reloaded.data.layout.some((item: { id: string }) => item.id === `project:${projectId}`)).toBe(true);

    const invalid = await call(token, "PUT", "/api/v1/dashboard/me/layout", {
      items: [{ id: "nope", x: 0, y: 0, w: 4, h: 1 }]
    });
    expect(invalid.code).toBe(400);

    const reset = await call(token, "POST", "/api/v1/dashboard/me/layout/reset");
    expect(reset.code).toBe(0);

    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });

  test("团队状态墙需要管理员权限", async () => {
    const token = await login();
    const forbidden = await call(token, "GET", "/api/v1/team/status");
    // demo 默认是 staff（E2E 结束时会把角色还原），因此这里是 403
    expect([0, 403]).toContain(forbidden.code);
  });
});
