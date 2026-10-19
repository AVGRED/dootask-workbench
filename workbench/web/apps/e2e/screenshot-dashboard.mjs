import fs from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";

const API = process.env.WORKBENCH_API ?? "http://127.0.0.1:9020";
const STAFF = "http://127.0.0.1:5180";
const OUT_DIR = path.resolve(import.meta.dirname, "../../../screenshots");

async function api(pathname, method = "GET", body, token) {
  const response = await fetch(`${API}${pathname}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
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

// 造一点数据，让首页板块不空
const projects = await api("/api/v1/projects?page=1&size=5", "GET", undefined, token);
let projectId = projects.list?.[0]?.id;
if (!projectId) {
  const created = await api("/api/v1/projects", "POST", { name: "产品迭代 · 首页演示" }, token);
  projectId = created.id;
}
const board = await api(`/api/v1/projects/${projectId}/board`, "GET", undefined, token);
const names = ["首页板块拖拽", "项目卡片进度", "日报入口", "状态切换"];
for (const name of names) {
  await api(`/api/v1/projects/${projectId}/tasks`, "POST", { name, pLevel: 1 }, token);
}
await api(`/api/v1/tasks/${board.columns[0].cards?.[0]?.id ?? 0}/move`.replace(/\/0\//, "/1/"), "PUT", { columnId: board.columns[1].column.id }, token).catch(() => {});
await api("/api/v1/me/status", "PUT", { status: "focus", note: "写首页" }, token);
console.log(`project=${projectId}`);

fs.mkdirSync(OUT_DIR, { recursive: true });

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.token", t);
  localStorage.setItem("workbench.user", JSON.stringify(u));
}, [token, login]);
const page = await context.newPage();
await page.goto(`${STAFF}/`, { waitUntil: "networkidle" });
await page.waitForSelector(".tile", { timeout: 20000 });
await page.waitForTimeout(1000);
await page.waitForTimeout(800);
await page.screenshot({ path: path.join(OUT_DIR, "dashboard.png") });

await browser.close();
console.log(`screenshot -> ${OUT_DIR}/dashboard.png`);
