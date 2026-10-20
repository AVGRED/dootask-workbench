import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const ADMIN = "http://localhost:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "admin", password: "a1s2d3f4!@#" })
})).json()).data;

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.admin.token", t);
  localStorage.setItem("workbench.admin.user", JSON.stringify(u));
}, [login.token, login]);
const page = await context.newPage();

for (const [route, file] of [
  ["projects", "ui-admin-projects.png"],
  ["reports", "ui-admin-reports.png"]
]) {
  await page.goto(`${ADMIN}/${route}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
}

await browser.close();
