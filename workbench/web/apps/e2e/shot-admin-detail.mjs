import path from "node:path";
import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = await (await fetch(`${BASE}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "401001", password: "HXG20061121." })
})).json();
const token = login.data.token;
const headers = { "Content-Type": "application/json", Authorization: `Bearer ${token}` };

const projects = await (await fetch(`${BASE}/api/v1/admin/projects`, { headers })).json();
const target = projects.data.find((p) => p.owner_userid !== 401001);
const task = await (await fetch(`${BASE}/api/v1/projects/${target.id}/tasks`, {
  method: "POST", headers, body: JSON.stringify({ name: "管理端详情演示任务", desc: "" })
})).json();
const taskId = task.data.id;
await fetch(`${BASE}/api/v1/tasks/${taskId}/comments`, {
  method: "POST", headers, body: JSON.stringify({ content: "这是管理端加的评论" })
});

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
  await page.locator(".card", { hasText: target.name }).first().click();
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, "admin-project-detail.png") });

  await page.getByRole("row", { name: /管理端详情演示任务/ }).getByRole("button", { name: "详情" }).click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, "admin-task-drawer.png") });
  console.log("shots done");
} finally {
  await browser.close();
  await fetch(`${BASE}/api/v1/tasks/${taskId}`, { method: "DELETE", headers });
}
