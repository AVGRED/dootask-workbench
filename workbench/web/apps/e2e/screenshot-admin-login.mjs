import path from "node:path";
import { chromium } from "@playwright/test";

const ADMIN = "http://localhost:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const browser = await chromium.launch({ channel: "chrome", headless: true });
for (const theme of ["light", "dark"]) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript((value) => localStorage.setItem("workbench.theme", value), theme);
  const page = await context.newPage();
  await page.goto(`${ADMIN}/login`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, `ui-admin-login-${theme}.png`) });
  console.log(`shot ui-admin-login-${theme}.png`);
  await context.close();
}
await browser.close();
