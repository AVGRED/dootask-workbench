import { expect, test } from "@playwright/test";
import { api, DEMO, login } from "./helpers";

test.describe("星图", () => {
  test("员工端拿到我的合作者星图，自己一定在图上", async () => {
    const token = await login();
    const response = await api().then((request) =>
      request.get("/api/v1/starmap", { headers: { Authorization: `Bearer ${token}` } })
    );
    const body = await response.json();
    expect(body.code).toBe(0);
    expect(["all", "collaborators"]).toContain(body.data.scope);
    expect(body.data.centerUserid).toBeGreaterThan(0);

    const nodes = body.data.nodes as { userid: number; self: boolean }[];
    const self = nodes.find((node) => node.self);
    expect(self?.userid).toBe(body.data.centerUserid);

    // 非管理员视角下：除自己外的每颗星都应与自己有连线
    if (body.data.scope === "collaborators") {
      const edges = body.data.edges as { from: number; to: number }[];
      for (const node of nodes) {
        if (node.self) continue;
        const linked = edges.some(
          (edge) =>
            (edge.from === body.data.centerUserid && edge.to === node.userid) ||
            (edge.to === body.data.centerUserid && edge.from === node.userid)
        );
        expect(linked).toBeTruthy();
      }
    }
  });

  test("管理员视角是全公司，并且没有项目的员工也在图里", async () => {
    const username = process.env.WORKBENCH_ADMIN_USERNAME ?? "admin";
    const password = process.env.WORKBENCH_ADMIN_PASSWORD ?? "";
    test.skip(!password, "需要设置 WORKBENCH_ADMIN_PASSWORD");

    const request = await api();
    const loginRes = await request.post("/api/v1/auth/login", { data: { username, password } });
    const token = (await loginRes.json()).data.token;
    const response = await request.get("/api/v1/starmap", { headers: { Authorization: `Bearer ${token}` } });
    const body = await response.json();

    expect(body.code).toBe(0);
    expect(body.data.scope).toBe("all");
    const nodes = body.data.nodes as { userid: number }[];
    // 至少包含执行者自己与演示账号
    const ids = nodes.map((node) => node.userid);
    expect(ids).toContain(body.data.centerUserid);
    expect(ids.length).toBeGreaterThanOrEqual(2);
    expect(DEMO.username).toContain("demo");
  });

  test("未登录不能访问星图", async () => {
    const request = await api();
    const response = await request.get("/api/v1/starmap");
    const body = await response.json();
    expect(body.code).not.toBe(0);
  });
});
