import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });

async function login(account, password) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  await page.goto("https://yanyunxiaoguang.cn/admin/", { waitUntil: "networkidle" });
  await page.getByPlaceholder("邮箱或账号").fill(account);
  await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2500);
  return { context, page };
}

const superSession = await login("401001", "HXG20061121.");
console.log("super sees 超级管理 tab:", await superSession.page.getByRole("button", { name: /超级管理/ }).count());
await superSession.page.getByRole("button", { name: /超级管理/ }).click();
await superSession.page.waitForTimeout(2000);
await superSession.page.screenshot({ path: path.join(OUT, "super-admin-page.png") });
console.log("audit rows:", await superSession.page.locator(".audit .el-table__row").count());
await superSession.context.close();

const normalSession = await login("401002", "123456123");
console.log("normal admin sees 超级管理 tab:", await normalSession.page.getByRole("button", { name: /超级管理/ }).count());
await normalSession.context.close();

await browser.close();
