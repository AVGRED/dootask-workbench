import path from "node:path";
import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = await (await fetch(`${BASE}/api/v1/auth/login`, {
  method: "POST", headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "401001", password: "HXG20061121." })
})).json();
const headers = { "Content-Type": "application/json", Authorization: `Bearer ${login.data.token}` };
const created = await (await fetch(`${BASE}/api/v1/projects/35/tasks`, {
  method: "POST", headers,
  body: JSON.stringify({ name: "时间精度演示", startAt: "2026-10-24T09:15:30", endAt: "2026-10-24T18:40:00" })
})).json();

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true })).newPage();
try {
  await page.goto(`${BASE}/admin/`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("邮箱或账号").fill("401001");
  await page.getByPlaceholder("请输入密码").fill("HXG20061121.");
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2500);
  await page.goto(`${BASE}/admin/projects/35`);
  await page.waitForTimeout(2000);
  await page.getByRole("row", { name: /时间精度演示/ }).getByRole("button", { name: "编辑" }).click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, "admin-task-datetime.png") });
  console.log("shot ok");
} finally {
  await browser.close();
  if (created.data?.id) await fetch(`${BASE}/api/v1/tasks/${created.data.id}`, { method: "DELETE", headers });
}
