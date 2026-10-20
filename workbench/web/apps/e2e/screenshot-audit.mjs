import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://localhost:5180";
const ADMIN = "http://localhost:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: process.env.WORKBENCH_DEMO_PASSWORD ?? "" })
})).json()).data;

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.token", t);
  localStorage.setItem("workbench.user", JSON.stringify(u));
  localStorage.setItem("workbench.admin.token", t);
  localStorage.setItem("workbench.admin.user", JSON.stringify(u));
}, [login.token, login]);
const page = await context.newPage();

for (const [url, file] of [
  [`${STAFF}/`, "ui-dashboard-light.png"],
  [`${STAFF}/projects`, "ui-projects-light.png"],
  [`${ADMIN}/login`, "ui-admin-login.png"]
]) {
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1300);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
}

await browser.close();
