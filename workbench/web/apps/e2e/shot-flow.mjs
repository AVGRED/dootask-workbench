import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
const page = await context.newPage();

await page.goto("https://yanyunxiaoguang.cn/", { waitUntil: "networkidle" });
await page.getByPlaceholder("邮箱或账号").fill("401004");
await page.getByPlaceholder("请输入密码").fill("123456123");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(2500);

const headerStyle = await page.evaluate(() => {
  const header = document.querySelector(".app-header");
  const style = getComputedStyle(header);
  return { background: style.backgroundColor, backdrop: style.backdropFilter };
});
console.log("header:", JSON.stringify(headerStyle));

const sweep = await page.evaluate(() => {
  const el = document.querySelector(".wb-galaxy .sweep");
  if (!el) return null;
  return { animation: getComputedStyle(el).animationName, duration: getComputedStyle(el).animationDuration };
});
console.log("sweep:", JSON.stringify(sweep));

// 连续三帧，间隔 3 秒，用来确认流动可见
for (let i = 1; i <= 3; i += 1) {
  await page.screenshot({ path: path.join(OUT, `flow-light-${i}.png`) });
  if (i < 3) await page.waitForTimeout(3000);
}
console.log("shots flow-light-1..3");

await browser.close();
