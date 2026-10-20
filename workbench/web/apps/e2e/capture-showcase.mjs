import fs from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://localhost:5180";
const ADMIN = "http://localhost:5181";
const OUT_STAFF = path.resolve(import.meta.dirname, "../../apps/staff/src/assets/showcase");
const OUT_ADMIN = path.resolve(import.meta.dirname, "../../apps/admin/src/assets/showcase");

const login = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: process.env.WORKBENCH_DEMO_PASSWORD ?? "" })
})).json()).data;

const adminLogin = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "admin", password: process.env.WORKBENCH_ADMIN_PASSWORD ?? "" })
})).json()).data;

fs.mkdirSync(OUT_STAFF, { recursive: true });
fs.mkdirSync(OUT_ADMIN, { recursive: true });

const browser = await chromium.launch({ channel: "chrome", headless: true });

async function capture(url, token, user, outFile, clip) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  await context.addInitScript(([t, u]) => {
    localStorage.setItem("workbench.token", t);
    localStorage.setItem("workbench.user", JSON.stringify(u));
    localStorage.setItem("workbench.admin.token", t);
    localStorage.setItem("workbench.admin.user", JSON.stringify(u));
  }, [token, user]);
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: outFile, clip });
  console.log(`saved ${outFile}`);
  await context.close();
}

const projects = (await (await fetch(`${API}/api/v1/projects?page=1&size=5`, {
  headers: { Authorization: `Bearer ${login.token}` }
})).json()).data;
const projectId = projects.list[0]?.id;

await capture(`${STAFF}/`, login.token, login, path.join(OUT_STAFF, "dashboard.png"), { x: 20, y: 80, width: 1000, height: 620 });
await capture(`${STAFF}/projects/${projectId}/board`, login.token, login, path.join(OUT_STAFF, "board.png"), { x: 20, y: 60, width: 1000, height: 560 });
await capture(`${STAFF}/projects/${projectId}/gantt`, login.token, login, path.join(OUT_STAFF, "gantt.png"), { x: 20, y: 60, width: 1000, height: 520 });

await capture(`${ADMIN}/team`, adminLogin.token, adminLogin, path.join(OUT_ADMIN, "team.png"), { x: 20, y: 70, width: 1100, height: 560 });
await capture(`${ADMIN}/projects`, adminLogin.token, adminLogin, path.join(OUT_ADMIN, "projects.png"), { x: 20, y: 70, width: 1100, height: 600 });
await capture(`${ADMIN}/reports`, adminLogin.token, adminLogin, path.join(OUT_ADMIN, "reports.png"), { x: 20, y: 70, width: 1100, height: 560 });

await browser.close();
