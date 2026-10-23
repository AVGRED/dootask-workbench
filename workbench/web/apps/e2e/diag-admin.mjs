import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true })).newPage();
const errors = [];
page.on("console", (msg) => { if (msg.type() === "error") errors.push(msg.text()); });
page.on("pageerror", (error) => errors.push("PAGEERROR: " + error.message));

await page.goto(`${BASE}/admin/`, { waitUntil: "networkidle" });
await page.getByPlaceholder("邮箱或账号").fill("401001");
await page.getByPlaceholder("请输入密码").fill("HXG20061121.");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForTimeout(2500);
await page.getByRole("button", { name: /项目总览/ }).click();
await page.waitForTimeout(2000);
console.log("URL:", page.url());
console.log("stats:", await page.locator(".stat-card").count(), "title:", await page.locator(".page-title").innerText().catch(() => "none"));
await page.getByRole("button", { name: /星图/ }).click();
await page.waitForTimeout(2000);
console.log("starmap:", await page.locator(".starmap").count());
console.log("ERRORS:", errors.slice(0, 8).join(" | "));
await browser.close();
