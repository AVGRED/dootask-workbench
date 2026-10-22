import { expect, test } from "@playwright/test";
import { ADMIN, api, call, createProject, createTask, login, suffix } from "./helpers";

/**
 * 任务存在性泄露回归：对「存在但无权访问」与「根本不存在」的任务，
 * 接口必须返回同样的错误，避免调用方探测任务是否存在。
 */
test.describe("任务可见性", () => {
  test.skip(!process.env.WORKBENCH_ADMIN_PASSWORD, "需要设置 WORKBENCH_ADMIN_PASSWORD");

  test("无权访问与不存在返回同样的错误", async () => {
    const request = await api();
    const adminToken = await login(ADMIN);
    const staffToken = await login();

    const stamp = suffix();
    const outsiderEmail = `e2e-outsider-${stamp}@workbench.local`;
    const created = await request.post("/api/v1/users", {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { email: outsiderEmail, nickname: "局外人", password: "outsider123" }
    });
    const outsiderId = (await created.json()).data.userid;

    let projectId = 0;
    try {
      // 员工自己建一个项目 + 任务，保证不依赖历史数据
      projectId = await createProject(staffToken, `E2E 可见性项目 ${stamp}`);
      const taskId = await createTask(staffToken, projectId, { name: `E2E 可见性任务 ${stamp}` });

      const outsiderToken = (
        await (await request.post("/api/v1/auth/login", {
          data: { username: outsiderEmail, password: "outsider123" }
        })).json()
      ).data.token;

      const forbidden = await call(outsiderToken, "GET", `/api/v1/tasks/${taskId}/comments`);
      const missing = await call(outsiderToken, "GET", "/api/v1/tasks/999999999/comments");

      expect(forbidden.code).not.toBe(0);
      expect(missing.code).not.toBe(0);
      expect(forbidden.code).toBe(missing.code);
      expect(forbidden.message).toBe(missing.message);
    } finally {
      if (projectId) {
        await call(staffToken, "DELETE", `/api/v1/projects/${projectId}`);
      }
      await request.post("/api/v1/admin/users/batch", {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: { action: "delete", userids: [outsiderId] }
      });
    }
  });
});
