import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const html = path.join(OUT, "delivery-6.html");
const names = {
  shot1: "01-需求清单.png",
  shot2: "02-方案原型.png",
  shot3: "03-代码开发.png",
  shot4: "04-测试验收.png",
  shot5: "05-发布部署.png",
  shot6: "06-运维监控.png"
};

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await (await browser.newContext({ viewport: { width: 1660, height: 1200 }, deviceScaleFactor: 2 })).newPage();
await page.goto("file:///" + html.replace(/\\/g, "/"), { waitUntil: "networkidle" });
await page.waitForTimeout(600);

for (const [id, file] of Object.entries(names)) {
  await page.locator(`#${id}`).screenshot({ path: path.join(OUT, file) });
  console.log("saved", file);
}

await browser.close();
