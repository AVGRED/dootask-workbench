import path from "node:path";
import { chromium } from "@playwright/test";

const STAFF = "http://localhost:5180";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const browser = await chromium.launch({ channel: "chrome", headless: true });

for (const theme of ["light", "dark"]) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript((value) => {
    localStorage.setItem("workbench.theme", value);
  }, theme);
  const page = await context.newPage();
  await page.goto(`${STAFF}/login`, { waitUntil: "networkidle" });
  await page.waitForTimeout(900);
  const file = theme === "light" ? "ui-login-light.png" : "ui-login-dark.png";
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(`shot ${file}`);
  await context.close();
}

await browser.close();
