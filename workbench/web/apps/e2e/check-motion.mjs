import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });

// 关键：模拟"系统已关闭动画"的环境，验证银河仍然在动
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  ignoreHTTPSErrors: true,
  reducedMotion: "reduce"
});
const page = await context.newPage();

await page.goto("https://yanyunxiaoguang.cn/", { waitUntil: "networkidle" });
await page.getByPlaceholder("邮箱或账号").fill("401004");
await page.getByPlaceholder("请输入密码").fill("123456123");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(2500);

const read = async () =>
  page.evaluate(() => {
    const pick = (sel) => getComputedStyle(document.querySelector(sel));
    return {
      reduced: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
      bandA: pick(".wb-galaxy .band-a").transform,
      sweep: pick(".wb-galaxy .sweep").transform,
      sweepDuration: pick(".wb-galaxy .sweep").animationDuration,
      stars: pick(".wb-galaxy .stars").transform
    };
  });

const first = await read();
console.log("reduced-motion detected:", first.reduced);
console.log("sweep duration:", first.sweepDuration);
console.log("t1 band:", first.bandA);
console.log("t1 sweep:", first.sweep);

await page.screenshot({ path: path.join(OUT, "motion-check-1.png") });
await page.waitForTimeout(3000);
const second = await read();
console.log("t2 band:", second.bandA);
console.log("t2 sweep:", second.sweep);
await page.screenshot({ path: path.join(OUT, "motion-check-2.png") });

console.log("band moved:", first.bandA !== second.bandA);
console.log("sweep moved:", first.sweep !== second.sweep);
console.log("stars moved:", first.stars !== second.stars);

await browser.close();
