import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import UsersView from "../views/UsersView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    { path: "/", name: "users", component: UsersView }
  ]
});

router.beforeEach((to) => {
  const token = localStorage.getItem("workbench.admin.token");
  if (!token && !to.meta.public) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (token && to.name === "login") {
    return { name: "users" };
  }
  return true;
});
