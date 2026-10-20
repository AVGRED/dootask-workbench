import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const username = process.env.WORKBENCH_AVATAR_USER ?? "demo@workbench.local";
const password = process.env.WORKBENCH_AVATAR_PASSWORD ?? process.env.WORKBENCH_DEMO_PASSWORD ?? "";

const login = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username, password })
})).json()).data;

const browser = await chromium.launch({ channel: "chrome", headless: true });
const ctx = await browser.newContext({ viewport: { width: 240, height: 240 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();
const hue = username.includes("admin") ? "#4f46e5" : "#6366f1";
await page.setContent(`
  <body style="margin:0;display:flex;align-items:center;justify-content:center;height:240px;
    background:radial-gradient(circle at 30% 25%, #eef2ff, #c7d2fe 55%, ${hue} 100%);">
    <div style="position:relative;width:196px;height:196px;border-radius:50%;
      background:linear-gradient(160deg,#ffffff 0%,#e0e7ff 40%,#a5b4fc 100%);
      box-shadow:inset -14px -18px 34px rgba(79,70,229,.35), inset 10px 12px 22px rgba(255,255,255,.9), 0 14px 26px rgba(79,70,229,.25);
      display:flex;align-items:center;justify-content:center;">
      <span style="font-size:96px;line-height:1;transform:translateY(-4px)">${username.includes("admin") ? "🧑‍💻" : "🙂"}</span>
    </div>
  </body>`);
const buffer = await page.screenshot({ type: "png" });
await browser.close();

const form = new FormData();
form.append("file", new Blob([buffer], { type: "image/png" }), "avatar.png");
const json = await (await fetch(`${API}/api/v1/me/avatar`, {
  method: "POST",
  headers: { Authorization: `Bearer ${login.token}` },
  body: form
})).json();
console.log(`${username}: code=${json.code} file=${json.data?.avatar}`);
