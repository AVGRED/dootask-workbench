import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { router } from "./router";

const app = createApp(App).use(createPinia()).use(router).use(ElementPlus);

window.addEventListener("workbench-token-expired", () => {
  void router.replace({ name: "login" });
});

app.mount("#app");
