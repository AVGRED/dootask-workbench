import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://localhost:5180";

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
}, [login.token, login]);
const page = await context.newPage();
page.on("console", (msg) => {
  if (msg.type() === "error" || msg.type() === "warning") console.log(`[${msg.type()}] ${msg.text()}`);
});
page.on("pageerror", (error) => console.log(`[pageerror] ${error.message}`));

await page.goto(`${STAFF}/projects/9084/report`, { waitUntil: "networkidle" });
await page.waitForTimeout(1500);
const text = (await page.locator("body").innerText()).slice(0, 300);
console.log(`body=${text.replace(/\s+/g, " ")}`);
await browser.close();
