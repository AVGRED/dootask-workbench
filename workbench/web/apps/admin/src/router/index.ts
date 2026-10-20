import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import AdminLayout from "../views/AdminLayout.vue";
import UsersView from "../views/UsersView.vue";
import SettingsView from "../views/SettingsView.vue";
import TeamStatusView from "../views/TeamStatusView.vue";
import ProjectsOverviewView from "../views/ProjectsOverviewView.vue";
import DailyReportsView from "../views/DailyReportsView.vue";
import StarMapView from "../views/StarMapView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    {
      path: "/",
      component: AdminLayout,
      children: [
        { path: "", redirect: { name: "team" } },
        { path: "team", name: "team", component: TeamStatusView },
        { path: "projects", name: "projects", component: ProjectsOverviewView },
        { path: "reports", name: "reports", component: DailyReportsView },
        { path: "starmap", name: "starmap", component: StarMapView },
        { path: "users", name: "users", component: UsersView },
        { path: "settings", name: "settings", component: SettingsView }
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
