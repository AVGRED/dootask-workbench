import { defineConfig, devices } from "@playwright/test";

/**
 * 浏览器验收：默认使用本机已安装的 Chrome（channel），CI 中设置 PW_CHANNEL=chromium
 * 使用 Playwright 自带浏览器（无需下载 Chrome）。
 * 前置：后端跑在 9000；staff(5180)/admin(5181)/mobile(5182) 三端 dev server 已启动。
 */
const channel = process.env.PW_CHANNEL ?? "chrome";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  outputDir: "test-results",
  use: {
    ...devices["Desktop Chrome"],
    channel: channel === "chromium" ? undefined : channel,
    headless: true,
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  }
});
