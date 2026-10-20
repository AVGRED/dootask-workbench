import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/api",
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  reporter: [["list"]],
  use: {
    baseURL: process.env.WORKBENCH_API ?? "http://127.0.0.1:9020",
    extraHTTPHeaders: { "Content-Type": "application/json" }
  }
});
