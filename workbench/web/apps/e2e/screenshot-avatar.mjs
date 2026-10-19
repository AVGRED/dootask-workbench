import fs from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const ADMIN = "http://127.0.0.1:5181";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const loginResponse = await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: "secret123" })
});
const login = (await loginResponse.json()).data;
const auth = { Authorization: `Bearer ${login.token}` };

// 让 demo 成为管理员，保证能打开管理端三个页面
await fetch(`${API}/api/v1/users/${login.userid}/role`, {
  method: "PUT",
  headers: { ...auth, "Content-Type": "application/json" },
  body: JSON.stringify({ role: "admin" })
});

const browser = await chromium.launch({ channel: "chrome", headless: true });

// 1) 用浏览器渲染一个 3D 风格头像并导出 PNG
const avatarContext = await browser.newContext({ viewport: { width: 240, height: 240 }, deviceScaleFactor: 2 });
const avatarPage = await avatarContext.newPage();
await avatarPage.setContent(`
  <body style="margin:0;display:flex;align-items:center;justify-content:center;height:240px;
    background:radial-gradient(circle at 30% 25%, #eaf4ff, #b9d4f7 55%, #8db6e8 100%);">
    <div style="position:relative;width:200px;height:200px;border-radius:50%;
      background:linear-gradient(160deg,#ffffff 0%,#dbe8fb 40%,#a8c6ee 100%);
      box-shadow:inset -14px -18px 34px rgba(64,158,255,.35), inset 10px 12px 22px rgba(255,255,255,.9), 0 14px 26px rgba(64,158,255,.28);
      display:flex;align-items:center;justify-content:center;">
      <span style="font-size:104px;line-height:1;transform:translateY(-4px)">🙂</span>
      <div style="position:absolute;bottom:-14px;left:34px;right:34px;height:16px;border-radius:50%;
        background:radial-gradient(ellipse at center, rgba(64,158,255,.35), transparent 70%);"></div>
    </div>
  </body>`);
const avatarBuffer = await avatarPage.screenshot({ type: "png" });
await avatarContext.close();

const form = new FormData();
form.append("file", new Blob([avatarBuffer], { type: "image/png" }), "avatar.png");
const uploadResponse = await fetch(`${API}/api/v1/me/avatar`, { method: "POST", headers: auth, body: form });
const upload = await uploadResponse.json();
console.log(`avatar=${upload.code === 0 ? upload.data.avatar : "FAILED " + upload.code}`);

// 2) 截图管理端状态墙
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  localStorage.setItem("workbench.admin.token", t);
  localStorage.setItem("workbench.admin.user", JSON.stringify(u));
}, [login.token, login]);
const page = await context.newPage();
await page.goto(`${ADMIN}/team`, { waitUntil: "networkidle" });
await page.waitForSelector(".person", { timeout: 20000 });
await page.waitForTimeout(800);
await page.screenshot({ path: path.join(OUT, "admin-status.png") });
await browser.close();
console.log(`screenshot -> ${OUT}/admin-status.png`);
