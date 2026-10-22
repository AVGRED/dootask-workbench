import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
const page = await context.newPage();

await page.goto("https://yanyunxiaoguang.cn/admin/", { waitUntil: "networkidle" });
await page.getByPlaceholder("邮箱或账号").fill("401001");
await page.getByPlaceholder("请输入密码").fill("123456123");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(2500);
await page.getByRole("button", { name: "用户管理" }).click();
await page.waitForTimeout(2000);
await page.screenshot({ path: path.join(OUT, "prod-admin-users-super.png") });
console.log("badge count:", await page.getByText("超级管理员").count());

await browser.close();
