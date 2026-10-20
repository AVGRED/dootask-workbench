import fs from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";

const BRAND = path.resolve(import.meta.dirname, "../../../brand");
const OUT = path.join(BRAND, "png");
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: "chrome", headless: true });

async function render(svgFile, outFile, width, height, background = null) {
  const svg = fs.readFileSync(path.join(BRAND, svgFile), "utf8");
  const scaled = svg.replace(/width="128" height="128"/, `width="${width}" height="${height}"`);
  const context = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await page.setContent(
    `<html><body style="margin:0;display:flex;align-items:center;justify-content:center;width:${width}px;height:${height}px;background:${background ?? "transparent"}">${scaled}</body></html>`
  );
  await page.waitForTimeout(200);
  await page.screenshot({ path: path.join(OUT, outFile), omitBackground: true });
  console.log(`rendered ${outFile}`);
  await context.close();
}

await render("logo-mark.svg", "icon-512.png", 512, 512);
await render("logo-mark.svg", "icon-192.png", 192, 192);
await render("logo-mark.svg", "icon-64.png", 64, 64);
await render("logo-mark-compact.svg", "icon-32.png", 32, 32);
await render("logo-mark-compact.svg", "icon-64-compact.png", 64, 64);
await render("logo-mark.svg", "icon-512-light.png", 512, 512, "#f5f6fa");
await render("logo-mark-mono.svg", "mark-mono.png", 256, 256, "#ffffff");
await render("logo-horizontal.svg", "horizontal.png", 640, 192, "#ffffff");

await browser.close();
console.log(`-> ${OUT}`);
