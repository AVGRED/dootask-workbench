import path from "node:path";
import { chromium } from "@playwright/test";

const OUT = path.resolve(import.meta.dirname, "../../../screenshots");
const browser = await chromium.launch({ channel: "chrome", headless: true });

async function shot({ url, account, password, theme, file, width = 1440, height = 900 }) {
  const context = await browser.newContext({ viewport: { width, height }, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.getByPlaceholder("邮箱或账号").fill(account);
  await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2500);
  if (theme === "dark") {
    await page.evaluate(() => {
      localStorage.setItem("workbench.theme", "dark");
      document.documentElement.classList.add("dark");
    });
    await page.waitForTimeout(800);
  }
  await page.screenshot({ path: path.join(OUT, file) });
  console.log("shot", file);
  await context.close();
}

await shot({
  url: "https://yanyunxiaoguang.cn/",
  account: "401004",
  password: "123456123",
  theme: "light",
  file: "prod-bg-staff-light.png"
});
await shot({
  url: "https://yanyunxiaoguang.cn/admin/",
  account: "401001",
  password: "HXG20061121.",
  theme: "light",
  file: "prod-bg-admin-light.png"
});
await shot({
  url: "https://yanyunxiaoguang.cn/",
  account: "401004",
  password: "123456123",
  theme: "dark",
  file: "prod-bg-staff-dark.png"
});

await browser.close();
