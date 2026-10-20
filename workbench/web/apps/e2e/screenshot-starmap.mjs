import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://localhost:5180";
const ADMIN = "http://localhost:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

async function login(username, password) {
  return (await (await fetch(`${API}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  })).json()).data;
}

const demo = await login("401004", "123456");
const legacy = await login("401005", "123456");
const admin = await login("admin", process.env.WORKBENCH_ADMIN_PASSWORD ?? "");

const browser = await chromium.launch({ channel: "chrome", headless: true });

async function shot(url, token, user, file, theme = "light") {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript(([t, u, th]) => {
    localStorage.setItem("workbench.theme", th);
    localStorage.setItem("workbench.token", t);
    localStorage.setItem("workbench.user", JSON.stringify(u));
    localStorage.setItem("workbench.admin.token", t);
    localStorage.setItem("workbench.admin.user", JSON.stringify(u));
  }, [token, user, theme]);
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1600);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
  await context.close();
}

await shot(`${STAFF}/starmap`, legacy.token, legacy, "ui-starmap-staff.png");
await shot(`${ADMIN}/starmap`, admin.token, admin, "ui-starmap-admin.png");
await shot(`${ADMIN}/starmap`, admin.token, admin, "ui-starmap-admin-dark.png", "dark");

await browser.close();
