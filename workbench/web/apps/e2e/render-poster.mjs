import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const html = path.join(OUT, "poster-9x16.html");

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await (await browser.newContext({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 2 })).newPage();
await page.goto("file:///" + html.replace(/\\/g, "/"), { waitUntil: "networkidle" });
await page.waitForTimeout(600);
await page.screenshot({ path: path.join(OUT, "星图海报-9x16.png") });
await browser.close();
console.log("poster saved");
