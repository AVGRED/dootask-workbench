import { expect, test } from "@playwright/test";
import { call, createProject, createTask, login, suffix } from "./helpers";

test.describe("项目与任务", () => {
  test("项目 CRUD 与任务列表", async () => {
    const token = await login();
    const projectId = await createProject(token, `API 项目 ${suffix()}`);

    const listed = await call(token, "GET", `/api/v1/projects?page=1&size=100`);
    expect(listed.code).toBe(0);
    expect(listed.data.list.some((item: { id: number }) => item.id === projectId)).toBe(true);

    const taskId = await createTask(token, projectId, { name: "API 测试任务", pLevel: 1 });
    const tasks = await call(token, "GET", `/api/v1/projects/${projectId}/tasks`);
    expect(tasks.data.list.some((item: { id: number }) => item.id === taskId)).toBe(true);

    const updated = await call(token, "PUT", `/api/v1/tasks/${taskId}`, { name: "改名后的任务" });
    expect(updated.code).toBe(0);

    const completed = await call(token, "POST", `/api/v1/tasks/${taskId}/complete`, {});
    expect(completed.code).toBe(0);

    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });

  test("删除有子任务的任务会被拒绝", async () => {
    const token = await login();
    const projectId = await createProject(token);
    const parentId = await createTask(token, projectId, { name: "父任务" });
    const childId = await createTask(token, projectId, { name: "子任务", parentId });

    const rejected = await call(token, "DELETE", `/api/v1/tasks/${parentId}`);
    expect(rejected.code).not.toBe(0);
    expect(rejected.message).toContain("子任务");

    await call(token, "DELETE", `/api/v1/tasks/${childId}`);
    const ok = await call(token, "DELETE", `/api/v1/tasks/${parentId}`);
    expect(ok.code).toBe(0);
    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });

  test("批量完成与 CSV 导出", async () => {
    const token = await login();
    const projectId = await createProject(token);
    const first = await createTask(token, projectId, { name: "批量A" });
    const second = await createTask(token, projectId, { name: "批量B" });

    const batch = await call(token, "POST", `/api/v1/projects/${projectId}/tasks/batch`, {
      action: "complete",
      taskIds: [first, second]
    });
    expect(batch.data.affected).toBe(2);

    const context = await (await import("./helpers")).api();
    const csv = await context.get(`/api/v1/projects/${projectId}/tasks/export`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(await csv.text()).toContain("id,name");

    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });
});
