import path from "node:path";
import { chromium } from "@playwright/test";

const API = "http://127.0.0.1:9020";
const MOBILE = "http://localhost:5182";
const OUT = path.resolve(import.meta.dirname, "../../../screenshots");

const login = (await (await fetch(`${API}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "demo@workbench.local", password: process.env.WORKBENCH_DEMO_PASSWORD ?? "" })
})).json()).data;

const browser = await chromium.launch({ channel: "chrome", headless: true });

const pages = [
  ["/", "mobile-home"],
  ["/projects", "mobile-projects"],
  ["/report", "mobile-report"],
  ["/profile", "mobile-profile"]
];

for (const theme of ["light", "dark"]) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  await context.addInitScript(([t, u, th]) => {
    localStorage.setItem("workbench.mobile.token", t);
    localStorage.setItem("workbench.mobile.user", JSON.stringify(u));
    localStorage.setItem("workbench.token", t);
    localStorage.setItem("workbench.user", JSON.stringify(u));
    localStorage.setItem("workbench.theme", th);
  }, [login.token, login, theme]);
  const page = await context.newPage();
  for (const [route, name] of pages) {
    await page.goto(`${MOBILE}${route}`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1200);
    await page.screenshot({ path: path.join(OUT, `${name}-${theme}.png`) });
    console.log(`shot ${name}-${theme}.png`);
  }
  await context.close();
}

await browser.close();
