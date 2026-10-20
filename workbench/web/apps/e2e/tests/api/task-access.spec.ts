import { expect, test } from "@playwright/test";
import { api, call, login } from "./helpers";

/**
 * 任务存在性泄露回归：对"存在但无权访问"与"根本不存在"的任务，
 * 接口必须返回同样的错误，避免调用方探测任务是否存在。
 */
test.describe("任务可见性", () => {
  test("无权访问与不存在返回同样的错误", async () => {
    const adminUsername = process.env.WORKBENCH_ADMIN_USERNAME ?? "admin";
    const adminPassword = process.env.WORKBENCH_ADMIN_PASSWORD ?? "";
    test.skip(!adminPassword, "需要设置 WORKBENCH_ADMIN_PASSWORD");

    const request = await api();
    const adminToken = (
      await (await request.post("/api/v1/auth/login", {
        data: { username: adminUsername, password: adminPassword }
      })).json()
    ).data.token;

    const stamp = Date.now();
    const outsiderEmail = `e2e-outsider-${stamp}@workbench.local`;
    const created = await request.post("/api/v1/users", {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { email: outsiderEmail, nickname: "局外人", password: "outsider123" }
    });
    const outsiderId = (await created.json()).data.userid;

    try {
      // 局外人登录
      const outsiderToken = (
        await (await request.post("/api/v1/auth/login", {
          data: { username: outsiderEmail, password: "outsider123" }
        })).json()
      ).data.token;

      // demo 的项目里有任务；局外人不在该项目
      const demoToken = await login();
      const projects = (await call(demoToken, "GET", "/api/v1/projects?page=1&size=5")).data;
      const projectId = projects.list[0].id;
      const tasks = (await call(demoToken, "GET", `/api/v1/projects/${projectId}/tasks?page=1&size=5`)).data;
      const existingTaskId = tasks.list[0]?.id ?? tasks[0]?.id;
      expect(existingTaskId).toBeTruthy();

      const forbidden = await call(outsiderToken, "GET", `/api/v1/tasks/${existingTaskId}/comments`);
      const missing = await call(outsiderToken, "GET", "/api/v1/tasks/999999999/comments");

      expect(forbidden.code).not.toBe(0);
      expect(missing.code).not.toBe(0);
      // 两条路径必须给出完全一样的错误，才无法据此判断任务是否存在
      expect(forbidden.code).toBe(missing.code);
      expect(forbidden.message).toBe(missing.message);
    } finally {
      await request.post("/api/v1/admin/users/batch", {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: { action: "delete", userids: [outsiderId] }
      });
    }
  });
});
