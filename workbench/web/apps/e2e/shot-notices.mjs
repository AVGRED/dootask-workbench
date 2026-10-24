import path from "node:path";
import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });

async function shot(url, user, pass, file, after) {
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true })).newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (msg) => { if (msg.type() === "error") errors.push(msg.text()); });
  await page.goto(`${BASE}${url}`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("邮箱或账号").fill(user);
  await page.getByPlaceholder("请输入密码").fill(pass);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2500);
  if (after) await after(page);
  await page.screenshot({ path: path.join(OUT, file) });
  console.log(file, "errors:", errors.slice(0, 3).join(" | ") || "none");
}

await shot("/admin/", "401001", "HXG20061121.", "notice-admin.png", async (page) => {
  await page.goto(`${BASE}/admin/notices`);
  await page.waitForTimeout(1800);
});

await shot("/", "401008", "123456123", "notice-staff.png", async (page) => {
  await page.goto(`${BASE}/projects`);
  await page.waitForTimeout(2000);
  const bell = page.locator("button", { hasText: "通知" }).first();
  if (await bell.count()) {
    await bell.click();
    await page.waitForTimeout(1200);
  }
});

await browser.close();
