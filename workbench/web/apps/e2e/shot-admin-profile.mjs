import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, ignoreHTTPSErrors: true });
const page = await context.newPage();
const failures = [];
page.on("response", (response) => {
  if (response.status() >= 400) failures.push(`${response.status()} ${response.url()}`);
});

await page.goto("https://yanyunxiaoguang.cn/admin/", { waitUntil: "networkidle" });
await page.getByPlaceholder("邮箱或账号").fill("401001");
await page.getByPlaceholder("请输入密码").fill("HXG20061121.");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(2500);

await page.getByRole("button", { name: "设置" }).click();
await page.waitForTimeout(2000);

console.log("profile card:", await page.getByText("我的资料").count());
console.log("nickname value:", await page.locator(".profile-form input").first().inputValue());
console.log("email value:", await page.locator(".profile-form input").nth(1).inputValue());
console.log("save button:", await page.getByRole("button", { name: "保存信息" }).count());

// 改职位并保存，验证写入
const professionInput = page.locator(".profile-form input").nth(2);
await professionInput.fill("超级管理员");
await page.getByRole("button", { name: "保存信息" }).click();
await page.waitForTimeout(1500);
const message = await page.locator(".el-message").first().innerText().catch(() => "(none)");
console.log("message:", message.replace(/\n/g, " "));

await page.screenshot({ path: path.join(OUT, "prod-admin-settings-profile.png"), fullPage: false });
console.log("failures:", failures.length, failures.slice(0, 3).join(" | "));

await browser.close();
