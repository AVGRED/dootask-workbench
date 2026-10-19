import path from "node:path";
import { chromium } from "@playwright/test";

const API = process.env.WORKBENCH_API ?? "http://127.0.0.1:9020";
const ADMIN = "http://127.0.0.1:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

async function api(pathname, method = "GET", body, token) {
  const response = await fetch(`${API}${pathname}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const json = await response.json();
  return json;
}

const login = await api("/api/v1/auth/login", "POST", {
  username: "demo@workbench.local",
  password: "secret123"
});
const token = login.data.token;
const today = new Date().toISOString().slice(0, 10);

// 造一份今日日报，让日报 tab 有内容
const report = await api("/api/v1/reports/daily", "POST", {
  contentDone: "完成首页 Bento 板块与项目板块",
  contentBlocker: "管理端需要新增项目总览",
  contentPlan: "补充三个 tab 的截图",
  minutes: 210
}, token);
console.log(`report=${report.code}`);

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.admin.token", t);
  localStorage.setItem("workbench.admin.user", JSON.stringify(u));
}, [token, login.data]);
const page = await context.newPage();

for (const [route, file] of [["team", "admin-status.png"], ["projects", "admin-projects.png"], ["reports", "admin-reports.png"]]) {
  await page.goto(`${ADMIN}/${route}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
}

await browser.close();
console.log(`today=${today} -> ${OUT}`);
