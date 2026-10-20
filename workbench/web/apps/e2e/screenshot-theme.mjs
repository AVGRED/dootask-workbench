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

async function shot(url, file, theme) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript(([t, u, th]) => {
    localStorage.setItem("workbench.token", t);
    localStorage.setItem("workbench.user", JSON.stringify(u));
    localStorage.setItem("workbench.admin.token", t);
    localStorage.setItem("workbench.admin.user", JSON.stringify(u));
    localStorage.setItem("workbench.theme", th);
  }, [login.token, login, theme]);
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, file) });
  await context.close();
  console.log(`shot ${file} (${theme})`);
}

await shot(`${STAFF}/`, "ui-dashboard-light.png", "light");
await shot(`${STAFF}/`, "ui-dashboard-dark.png", "dark");
await shot(`${STAFF}/projects`, "ui-projects-light.png", "light");
await shot(`${ADMIN}/team`, "ui-admin-light.png", "light");
await shot(`${ADMIN}/team`, "ui-admin-dark.png", "dark");

await browser.close();
console.log(`-> ${OUT}`);
