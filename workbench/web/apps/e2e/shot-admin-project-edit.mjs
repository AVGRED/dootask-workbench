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
const created = await (await fetch(`${BASE}/api/v1/projects`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
  body: JSON.stringify({ name: "管理端项目编辑演示", desc: "演示用，截图后删除" })
})).json();
const projectId = created.data.id;

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
  await page.waitForTimeout(1500);

  const card = page.locator(".card", { hasText: "管理端项目编辑演示" }).first();
  await card.hover();
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(OUT, "admin-project-actions.png") });

  await card.getByRole("button", { name: "编辑" }).click();
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(OUT, "admin-project-edit.png") });

  await page.getByRole("button", { name: "取消" }).click();
  await card.getByRole("button", { name: "成员" }).click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, "admin-project-members.png") });
  console.log("shots done");
} finally {
  await browser.close();
  await fetch(`${BASE}/api/v1/projects/${projectId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` }
  });
  console.log("cleanup project", projectId);
}
