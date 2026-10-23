import path from "node:path";
import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = await (await fetch(`${BASE}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "401001", password: "HXG20061121." })
})).json();
const headers = { Authorization: `Bearer ${login.data.token}` };
const projects = await (await fetch(`${BASE}/api/v1/admin/projects`, { headers })).json();
const project = projects.data[0];

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
const page = await context.newPage();
try {
  await page.goto(`${BASE}/admin/`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("邮箱或账号").fill("401001");
  await page.getByPlaceholder("请输入密码").fill("HXG20061121.");
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2500);
  await page.getByRole("button", { name: /项目总览/ }).click();
  await page.waitForTimeout(1200);
  await page.locator(".card", { hasText: project.name }).first().click();
  await page.waitForTimeout(1500);
  await page.getByRole("button", { name: "成员管理" }).click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, "admin-project-members.png") });
  console.log("shot ok");
} finally {
  await browser.close();
}
