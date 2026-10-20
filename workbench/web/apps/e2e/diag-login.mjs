import { chromium } from "@playwright/test";

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on("console", (m) => {
  if (m.type() === "error" || m.type() === "warning") console.log(`[${m.type()}] ${m.text()}`);
});
page.on("pageerror", (e) => console.log(`[pageerror] ${e.message}`));
await page.goto("http://localhost:5180/login", { waitUntil: "networkidle" });
await page.waitForTimeout(1200);
console.log("body=", (await page.locator("body").innerText()).slice(0, 200).replace(/\s+/g, " "));
await browser.close();
