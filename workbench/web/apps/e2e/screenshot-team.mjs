import path from "node:path";
import { chromium } from "@playwright/test";

const API = process.env.WORKBENCH_API ?? "http://127.0.0.1:9020";
const ADMIN = "http://127.0.0.1:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots/team-wall.png");

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

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.admin.token", t);
  localStorage.setItem("workbench.admin.user", JSON.stringify(u));
}, [token, login]);
const page = await context.newPage();
await page.goto(`${ADMIN}/team`, { waitUntil: "networkidle" });
await page.waitForSelector(".person", { timeout: 20000 });
await page.waitForTimeout(600);
await page.screenshot({ path: OUT });
await browser.close();
console.log(`screenshot -> ${OUT}`);
