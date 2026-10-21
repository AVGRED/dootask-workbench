import path from "node:path";
import { chromium } from "@playwright/test";

const ADMIN = "https://yanyunxiaoguang.cn/admin/";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
const page = await context.newPage();
const failures = [];
page.on("response", (response) => {
  if (response.status() >= 400) failures.push(`${response.status()} ${response.url()}`);
});

await page.goto(ADMIN, { waitUntil: "networkidle" });
await page.getByPlaceholder("邮箱或账号").fill("401001");
await page.getByPlaceholder("请输入密码").fill("123456123");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(2500);

await page.getByRole("button", { name: "设置" }).click();
await page.waitForTimeout(2000);

const heading = page.getByText("修改我的密码");
console.log("card visible:", await heading.count());

// 故意输错原密码，验证只校验自己这条通道
await page.getByPlaceholder("当前密码").fill("wrong-password");
await page.getByPlaceholder("至少 8 位").fill("abcdefgh123");
await page.getByPlaceholder("再输入一次").fill("abcdefgh123");
await page.getByRole("button", { name: "更新密码" }).click();
await page.waitForTimeout(1500);

const message = await page.locator(".el-message").first().innerText().catch(() => "(no message)");
console.log("message:", message.replace(/\n/g, " "));

await page.screenshot({ path: path.join(OUT, "prod-admin-settings-password.png") });
console.log("failures:", failures.length, failures.slice(0, 3).join(" | "));

await browser.close();
