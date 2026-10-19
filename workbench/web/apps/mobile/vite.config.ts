import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const apiTarget = process.env.WORKBENCH_API ?? "http://127.0.0.1:9000";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5182,
    proxy: {
      "/api": {
        target: apiTarget,
        changeOrigin: true
      }
    }
  }
});
