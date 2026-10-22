import { createRouter, createWebHistory } from "vue-router";
import AdminLayout from "../views/AdminLayout.vue";

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/login", name: "login", component: () => import("../views/LoginView.vue"), meta: { public: true } },
    {
      path: "/",
      component: AdminLayout,
      children: [
        { path: "", redirect: { name: "team" } },
        { path: "team", name: "team", component: () => import("../views/TeamStatusView.vue") },
        { path: "projects", name: "projects", component: () => import("../views/ProjectsOverviewView.vue") },
        { path: "reports", name: "reports", component: () => import("../views/DailyReportsView.vue") },
        { path: "starmap", name: "starmap", component: () => import("../views/StarMapView.vue") },
        { path: "notices", name: "notices", component: () => import("../views/NoticesView.vue") },
        { path: "super", name: "super", component: () => import("../views/SuperAdminView.vue") },
        { path: "users", name: "users", component: () => import("../views/UsersView.vue") },
        { path: "settings", name: "settings", component: () => import("../views/SettingsView.vue") }
      ]
    }
  ]
});

router.beforeEach((to) => {
  const token = localStorage.getItem("workbench.admin.token");
  if (!token && !to.meta.public) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (token && to.name === "login") {
    return { name: "team" };
  }
  return true;
});
