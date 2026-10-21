import path from "node:path";
import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const browser = await chromium.launch({ channel: "chrome", headless: true });

async function shot(url, file, viewport) {
  const context = await browser.newContext({ viewport, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
  await context.close();
}

await shot(`${BASE}/`, "prod-staff-login.png", { width: 1440, height: 900 });
await shot(`${BASE}/admin/`, "prod-admin-login.png", { width: 1440, height: 900 });
await shot(`${BASE}/m/`, "prod-mobile-login.png", { width: 390, height: 844 });

await browser.close();
