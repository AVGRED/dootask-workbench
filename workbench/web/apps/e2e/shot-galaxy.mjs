import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });

async function session(url, account, password, dark) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.getByPlaceholder("邮箱或账号").fill(account);
  await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2500);
  if (dark) {
    await page.evaluate(() => {
      localStorage.setItem("workbench.theme", "dark");
      document.documentElement.classList.add("dark");
    });
    await page.waitForTimeout(1000);
  }
  return { context, page };
}

// 浅色：两个时间点截图，用来确认光带确实在动
const light = await session("https://yanyunxiaoguang.cn/", "401004", "123456123", false);
await light.page.screenshot({ path: path.join(OUT, "galaxy-light-1.png") });
const t1 = await light.page.evaluate(() => getComputedStyle(document.querySelector(".wb-galaxy .band-a")).transform);
await light.page.waitForTimeout(4000);
await light.page.screenshot({ path: path.join(OUT, "galaxy-light-2.png") });
const t2 = await light.page.evaluate(() => getComputedStyle(document.querySelector(".wb-galaxy .band-a")).transform);
console.log("band transform changed:", t1 !== t2);
console.log("t1:", t1);
console.log("t2:", t2);
const starAnim = await light.page.evaluate(() => getComputedStyle(document.querySelector(".wb-galaxy .stars")).animationName);
console.log("stars animation:", starAnim);
await light.context.close();

// 深色
const dark = await session("https://yanyunxiaoguang.cn/admin/", "401001", "HXG20061121.", true);
await dark.page.screenshot({ path: path.join(OUT, "galaxy-dark.png") });
console.log("shot galaxy-dark.png");
await dark.context.close();

await browser.close();
