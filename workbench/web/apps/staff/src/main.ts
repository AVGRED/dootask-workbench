import { createApp } from "vue";
import { createPinia } from "pinia";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { router } from "./router";
import "./styles/tokens.css";
import { useTheme } from "./composables/useTheme";

useTheme().initTheme();

const app = createApp(App).use(createPinia()).use(router);

window.addEventListener("workbench-token-expired", () => {
  void router.replace({ name: "login" });
});

app.mount("#app");
