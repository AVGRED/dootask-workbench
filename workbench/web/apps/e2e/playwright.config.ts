import { defineConfig, devices } from "@playwright/test";

/**
 * 浏览器验收：使用本机已安装的 Chrome（channel），不下载 Playwright 自带浏览器。
 * 前置：后端跑在 9000；staff(5180)/admin(5181)/mobile(5182) 三端 dev server 已启动。
 */
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
    channel: "chrome",
    headless: true,
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  }
});
