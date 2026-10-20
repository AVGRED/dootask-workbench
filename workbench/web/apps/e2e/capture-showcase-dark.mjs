import fs from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://localhost:5180";
const ADMIN = "http://localhost:5181";
const OUT_STAFF = path.resolve(import.meta.dirname, "../../apps/staff/src/assets/showcase");
const OUT_ADMIN = path.resolve(import.meta.dirname, "../../apps/admin/src/assets/showcase");

async function login(username, password) {
  return (await (await fetch(`${API}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  })).json()).data;
}

const demo = await login("demo@workbench.local", process.env.WORKBENCH_DEMO_PASSWORD ?? "");
const admin = await login("admin", "a1s2d3f4!@#");

const projects = (await (await fetch(`${API}/api/v1/projects?page=1&size=5`, {
  headers: { Authorization: `Bearer ${demo.token}` }
})).json()).data;
const projectId = projects.list[0]?.id;

const browser = await chromium.launch({ channel: "chrome", headless: true });

async function capture(url, token, user, outFile, clip) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  await context.addInitScript(([t, u]) => {
    localStorage.setItem("workbench.theme", "dark");
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

await capture(`${STAFF}/`, demo.token, demo, path.join(OUT_STAFF, "dashboard-dark.png"), { x: 20, y: 80, width: 1000, height: 600 });
await capture(`${STAFF}/projects/${projectId}/board`, demo.token, demo, path.join(OUT_STAFF, "board-dark.png"), { x: 20, y: 60, width: 1000, height: 520 });
await capture(`${ADMIN}/team`, admin.token, admin, path.join(OUT_ADMIN, "team-dark.png"), { x: 20, y: 70, width: 1100, height: 540 });
await capture(`${ADMIN}/projects`, admin.token, admin, path.join(OUT_ADMIN, "projects-dark.png"), { x: 20, y: 70, width: 1100, height: 560 });

await browser.close();
