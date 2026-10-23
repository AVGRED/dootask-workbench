import path from "node:path";
import { chromium } from "@playwright/test";

const BASE = "https://yanyunxiaoguang.cn";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = await (await fetch(`${BASE}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "401001", password: "HXG20061121." })
})).json();
const headers = { "Content-Type": "application/json", Authorization: `Bearer ${login.data.token}` };
const projects = await (await fetch(`${BASE}/api/v1/admin/projects`, { headers })).json();
const project = projects.data.find((p) => p.name === "猫耳开发") ?? projects.data[0];

for (const name of ["看板演示A", "看板演示B", "看板演示C"]) {
  await fetch(`${BASE}/api/v1/projects/${project.id}/tasks`, {
    method: "POST", headers, body: JSON.stringify({ name, ownerUserid: 401002 })
  });
}

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
  await page.getByRole("tab", { name: "看板" }).click();
  await page.waitForTimeout(1800);
  await page.screenshot({ path: path.join(OUT, "admin-kanban.png") });
  console.log("shot ok", project.id);
} finally {
  await browser.close();
}
