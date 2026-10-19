import fs from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";

const API = process.env.WORKBENCH_API ?? "http://127.0.0.1:9020";
const STAFF = "http://127.0.0.1:5180";
const OUT_DIR = path.resolve(import.meta.dirname, "../../../screenshots");

async function api(pathname, method = "GET", body, token) {
  const response = await fetch(`${API}${pathname}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const json = await response.json();
  if (json.code !== 0) throw new Error(`${pathname} -> ${json.code} ${json.message}`);
  return json.data;
}

const login = await api("/api/v1/auth/login", "POST", {
  username: "demo@workbench.local",
  password: "secret123"
});
const token = login.token;

const project = await api("/api/v1/projects", "POST", { name: "产品迭代 · 看板演示", desc: "看板可视化截图" }, token);
const tasks = [];
for (const name of ["梳理需求清单", "接口联调", "前端看板拖拽", "回归测试"]) {
  tasks.push(await api(`/api/v1/projects/${project.id}/tasks`, "POST", { name, pLevel: 1 }, token));
}

const board = await api(`/api/v1/projects/${project.id}/board`, "GET", undefined, token);
const [, doing, done] = board.columns.map((group) => group.column.id);
await api(`/api/v1/tasks/${tasks[1].id}/move`, "PUT", { columnId: doing }, token);
await api(`/api/v1/tasks/${tasks[2].id}/move`, "PUT", { columnId: doing }, token);
await api(`/api/v1/tasks/${tasks[3].id}/move`, "PUT", { columnId: done }, token);
console.log(`project=${project.id} columns=${board.columns.map((g) => g.column.id).join(",")}`);

// 甘特图演示任务：带日期与依赖
const ganttTasks = [
  ["需求梳理", "2026-10-05T09:00:00", "2026-10-07T18:00:00"],
  ["接口开发", "2026-10-08T09:00:00", "2026-10-14T18:00:00"],
  ["前端联调", "2026-10-12T09:00:00", "2026-10-16T18:00:00"],
  ["回归测试", "2026-10-19T09:00:00", "2026-10-21T18:00:00"]
];
const created = [];
for (const [name, startAt, endAt] of ganttTasks) {
  created.push(await api(`/api/v1/projects/${project.id}/tasks`, "POST", { name, startAt, endAt }, token));
}
await api(`/api/v1/projects/${project.id}/dependencies`, "POST",
  { predecessorTaskId: created[0].id, successorTaskId: created[1].id }, token);
await api(`/api/v1/projects/${project.id}/dependencies`, "POST",
  { predecessorTaskId: created[1].id, successorTaskId: created[2].id }, token);
await api(`/api/v1/projects/${project.id}/dependencies`, "POST",
  { predecessorTaskId: created[2].id, successorTaskId: created[3].id }, token);

fs.mkdirSync(OUT_DIR, { recursive: true });

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
await context.addInitScript(
  ([t, u]) => {
    localStorage.setItem("workbench.token", t);
    localStorage.setItem("workbench.user", JSON.stringify(u));
  },
  [token, login]
);
const page = await context.newPage();

await page.goto(`${STAFF}/projects/${project.id}/board`, { waitUntil: "networkidle" });
await page.waitForSelector(".column .card", { timeout: 20000 });
await page.screenshot({ path: path.join(OUT_DIR, "kanban.png") });

await page.goto(`${STAFF}/projects/${project.id}/gantt`, { waitUntil: "networkidle" });
await page.waitForSelector(".bar", { timeout: 20000 });
await page.screenshot({ path: path.join(OUT_DIR, "gantt.png") });

await page.goto(`${STAFF}/`, { waitUntil: "networkidle" });
await page.screenshot({ path: path.join(OUT_DIR, "projects.png") });

await browser.close();
console.log(`screenshots -> ${OUT_DIR}`);
