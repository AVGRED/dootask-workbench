import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { router } from "./router";
import "./styles/tokens.css";
import { useTheme } from "./composables/useTheme";

useTheme().initTheme();

const app = createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn });

window.addEventListener("workbench-token-expired", () => {
  void router.replace({ name: "login" });
});

app.mount("#app");
