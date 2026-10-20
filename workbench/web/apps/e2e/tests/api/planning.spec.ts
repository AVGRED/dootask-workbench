import { expect, test } from "@playwright/test";
import { call, createProject, createTask, login } from "./helpers";

test.describe("看板与甘特", () => {
  test("看板懒创建三列并能移动任务", async () => {
    const token = await login();
    const projectId = await createProject(token);
    const taskId = await createTask(token, projectId, { name: "看板任务" });

    const board = await call(token, "GET", `/api/v1/projects/${projectId}/board`);
    expect(board.code).toBe(0);
    expect(board.data.columns.length).toBeGreaterThanOrEqual(3);

    const target = board.data.columns[1].column.id;
    const moved = await call(token, "PUT", `/api/v1/tasks/${taskId}/move`, { columnId: target });
    expect(moved.code).toBe(0);

    const after = await call(token, "GET", `/api/v1/projects/${projectId}/board`);
    const targetColumn = after.data.columns.find((item: { column: { id: number } }) => item.column.id === target);
    expect(targetColumn.cards.some((card: { id: number }) => card.id === taskId)).toBe(true);

    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });

  test("甘特返回任务、依赖与时间范围", async () => {
    const token = await login();
    const projectId = await createProject(token);
    const first = await createTask(token, projectId, {
      name: "甘特A",
      startAt: "2026-11-02T09:00:00",
      endAt: "2026-11-04T18:00:00"
    });
    const second = await createTask(token, projectId, {
      name: "甘特B",
      startAt: "2026-11-05T09:00:00",
      endAt: "2026-11-07T18:00:00"
    });
    await call(token, "POST", `/api/v1/projects/${projectId}/dependencies`, {
      predecessorTaskId: first,
      successorTaskId: second
    });

    const gantt = await call(token, "GET", `/api/v1/projects/${projectId}/gantt`);
    expect(gantt.data.tasks.length).toBeGreaterThanOrEqual(2);
    expect(gantt.data.rangeStart <= "2026-11-02").toBe(true);
    expect(gantt.data.rangeEnd >= "2026-11-07").toBe(true);
    expect(
      gantt.data.edges.some(
        (edge: { predecessorTaskId: number; successorTaskId: number }) =>
          edge.predecessorTaskId === first && edge.successorTaskId === second
      )
    ).toBe(true);

    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });

  test("项目报表统计工时与成员", async () => {
    const token = await login();
    const projectId = await createProject(token);
    const taskId = await createTask(token, projectId, { name: "报表任务" });
    await call(token, "POST", `/api/v1/tasks/${taskId}/worklogs`, {
      requestId: `api-wl-${Date.now()}`,
      minutes: 45,
      workDate: new Date().toISOString().slice(0, 10)
    });

    const report = await call(token, "GET", `/api/v1/projects/${projectId}/report`);
    expect(report.data.taskTotal).toBeGreaterThanOrEqual(1);
    expect(report.data.worklogMinutes).toBeGreaterThanOrEqual(45);
    expect(report.data.members.length).toBeGreaterThanOrEqual(1);

    await call(token, "DELETE", `/api/v1/projects/${projectId}`);
  });
});
