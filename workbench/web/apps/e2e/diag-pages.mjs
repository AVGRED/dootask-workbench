import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";

const loginResponse = await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: "secret123" })
});
const loginJson = await loginResponse.json();
const login = loginJson.data ?? {};
console.log(`login_code=${loginJson.code} token=${login.token ? "yes" : "no"}`);

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(([t, u]) => {
  if (t) {
    localStorage.setItem("workbench.token", t);
    localStorage.setItem("workbench.user", JSON.stringify(u));
    localStorage.setItem("workbench.admin.token", t);
    localStorage.setItem("workbench.admin.user", JSON.stringify(u));
  }
}, [login.token ?? "", login]);

const pages = [
  ["http://localhost:5180/", "diag-dashboard.png"],
  ["http://localhost:5180/projects", "diag-projects.png"],
  ["http://localhost:5181/team", "diag-team.png"]
];

for (const [url, file] of pages) {
  const page = await context.newPage();
  const errors = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errors.push(msg.text().slice(0, 200));
  });
  page.on("response", (res) => { if (res.status() === 404) errors.push("404 " + res.url()); });
  page.on("pageerror", (err) => errors.push("pageerror: " + String(err).slice(0, 200)));
  try {
    await page.goto(url, { waitUntil: "networkidle", timeout: 20000 });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `D:/repos/workbench/dootask/workbench/screenshots/${file}` });
    const bodyText = (await page.textContent("body"))?.slice(0, 120) ?? "";
    console.log(`\n== ${url}\nurl_now=${page.url()}\ntext=${bodyText.replace(/\s+/g, " ")}\nerrors=${errors.join(" | ") || "none"}`);
  } catch (exception) {
    console.log(`\n== ${url}\nFAILED ${String(exception).slice(0, 200)}`);
  }
  await page.close();
}

await browser.close();
