import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { router } from "./router";
import "./styles/tokens.css";
import { useTheme } from "./composables/useTheme";

useTheme().initTheme();
import "./styles.css";

const app = createApp(App).use(createPinia()).use(router);

window.addEventListener("workbench-token-expired", () => {
  void router.replace({ name: "login" });
});

app.mount("#app");

if ("serviceWorker" in navigator && import.meta.env.PROD) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {
      // 离线能力注册失败不影响正常使用。
    });
  });
}
