import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const MOBILE = "http://127.0.0.1:5182";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const loginResponse = await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: process.env.WORKBENCH_DEMO_PASSWORD ?? "" })
});
const login = (await loginResponse.json()).data;

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true
});
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.token", t);
  localStorage.setItem("workbench.user", JSON.stringify(u));
}, [login.token, login]);
const page = await context.newPage();

await page.goto(`${MOBILE}/`, { waitUntil: "networkidle" });
await page.waitForTimeout(1200);
await page.screenshot({ path: path.join(OUT, "mobile-home.png") });
console.log("shot mobile-home.png");

await page.goto(`${MOBILE}/profile`, { waitUntil: "networkidle" });
await page.waitForTimeout(1000);
await page.screenshot({ path: path.join(OUT, "mobile-profile.png") });
console.log("shot mobile-profile.png");

await browser.close();
console.log(`-> ${OUT}`);
