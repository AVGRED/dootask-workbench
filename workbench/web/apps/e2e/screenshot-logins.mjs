import path from "node:path";
import { chromium } from "@playwright/test";

const STAFF = "http://localhost:5180";
const ADMIN = "http://localhost:5181";
const MOBILE = "http://localhost:5182";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const browser = await chromium.launch({ channel: "chrome", headless: true });

async function shot(url, file, viewport, theme) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: viewport.width < 500 ? 2 : 1 });
  await context.addInitScript((value) => localStorage.setItem("workbench.theme", value), theme);
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(900);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
  await context.close();
}

const desktop = { width: 1440, height: 900 };
const phone = { width: 390, height: 844 };

await shot(`${STAFF}/login`, "ui-login-light.png", desktop, "light");
await shot(`${STAFF}/login`, "ui-login-dark.png", desktop, "dark");
await shot(`${ADMIN}/login`, "ui-admin-login-light.png", desktop, "light");
await shot(`${MOBILE}/login`, "mobile-login-light.png", phone, "light");
await shot(`${MOBILE}/login`, "mobile-login-dark.png", phone, "dark");

await browser.close();
