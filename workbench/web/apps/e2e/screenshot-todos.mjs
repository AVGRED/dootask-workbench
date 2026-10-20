import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://localhost:5180";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "401004", password: "123456" })
})).json()).data;

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.token", t);
  localStorage.setItem("workbench.user", JSON.stringify(u));
}, [login.token, login]);
const page = await context.newPage();
await page.goto(`${STAFF}/`, { waitUntil: "networkidle" });
await page.waitForTimeout(1500);
await page.screenshot({ path: path.join(OUT, "ui-dashboard-todos.png") });
console.log("shot ui-dashboard-todos.png");
await browser.close();
