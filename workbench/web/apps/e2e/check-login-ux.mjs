import path from "node:path";
import { chromium } from "@playwright/test";

const STAFF = "http://localhost:5180";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
await page.goto(`${STAFF}/login`, { waitUntil: "networkidle" });

// 空值提交 → 字段级错误 + 抖动
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(500);
const body = await page.locator("body").innerText();
console.log(`has_account_error=${body.includes("请输入账号")} has_password_error=${body.includes("请输入密码")}`);
await page.screenshot({ path: path.join(OUT, "ui-login-error.png") });

// 错误密码 → 后端错误气泡
await page.getByPlaceholder("邮箱或账号").fill("demo@workbench.local");
await page.getByPlaceholder("请输入密码").fill("wrong-password");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(1200);
const afterBody = await page.locator("body").innerText();
console.log(`backend_error_shown=${/密码|错误|失败/.test(afterBody)}`);
await page.screenshot({ path: path.join(OUT, "ui-login-error-backend.png") });

await browser.close();
