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
await page.screenshot({ path: path.join(OUT, "prod-admin-team-after.png") });
console.log("FAILURES:", failures.length, failures.slice(0, 3).join(" | "));

await browser.close();
