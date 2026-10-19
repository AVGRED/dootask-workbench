import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const STAFF = "http://127.0.0.1:5180";

const loginResponse = await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: "secret123" })
});
const login = (await loginResponse.json()).data;

// 复位默认布局，保证初始状态可控
await fetch(`${API}/api/v1/dashboard/me/layout/reset`, {
  method: "POST",
  headers: { Authorization: `Bearer ${login.token}` }
});

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.token", t);
  localStorage.setItem("workbench.user", JSON.stringify(u));
}, [login.token, login]);
const page = await context.newPage();
await page.goto(`${STAFF}/`, { waitUntil: "networkidle" });
await page.waitForSelector(".tile");

const before = await page.evaluate(() => {
  const tile = document.querySelector(".tile");
  return tile.getBoundingClientRect().width;
});

const handle = page.locator(".tile .resize").first();
const box = await handle.boundingBox();
if (!box) throw new Error("no resize handle");
await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
await page.mouse.down();
await page.mouse.move(box.x + box.width / 2 + 200, box.y + box.height / 2, { steps: 10 });
await page.mouse.up();
await page.waitForTimeout(600);

const after = await page.evaluate(() => {
  const tile = document.querySelector(".tile");
  return tile.getBoundingClientRect().width;
});

const layoutResponse = await fetch(`${API}/api/v1/dashboard/me`, {
  headers: { Authorization: `Bearer ${login.token}` }
});
const layout = (await layoutResponse.json()).data.layout;

console.log(`width_before=${Math.round(before)} width_after=${Math.round(after)} grew=${after > before}`);
console.log(`saved_first=${JSON.stringify(layout[0])}`);

await fetch(`${API}/api/v1/dashboard/me/layout/reset`, {
  method: "POST",
  headers: { Authorization: `Bearer ${login.token}` }
});
await browser.close();
