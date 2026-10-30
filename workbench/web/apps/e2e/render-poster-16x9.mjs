import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const html = path.join(OUT, "poster-16x9.html");

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await (await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 2 })).newPage();
await page.goto("file:///" + html.replace(/\\/g, "/"), { waitUntil: "networkidle" });
await page.waitForTimeout(600);
await page.screenshot({ path: path.join(OUT, "星图海报-16x9.png") });
await browser.close();
console.log("poster 16:9 saved");
