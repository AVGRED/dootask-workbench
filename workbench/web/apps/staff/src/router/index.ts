import { createRouter, createWebHistory } from "vue-router";

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/login", name: "login", component: () => import("../views/LoginView.vue"), meta: { public: true } },
    { path: "/", name: "dashboard", component: () => import("../views/DashboardView.vue") },
    { path: "/projects", name: "projects", component: () => import("../views/ProjectsView.vue") },
    { path: "/reports", name: "reports", component: () => import("../views/DailyReportView.vue") },
    { path: "/starmap", name: "starmap", component: () => import("../views/StarMapView.vue") },
    { path: "/settings", name: "settings", component: () => import("../views/SettingsView.vue") },
    { path: "/projects/:projectId/tasks", name: "tasks", component: () => import("../views/TasksView.vue") },
    { path: "/projects/:projectId/planning", name: "planning", component: () => import("../views/PlanningView.vue") },
    { path: "/projects/:projectId/board", name: "board", component: () => import("../views/KanbanView.vue") },
    { path: "/projects/:projectId/gantt", name: "gantt", component: () => import("../views/GanttView.vue") },
    { path: "/projects/:projectId/report", name: "report", component: () => import("../views/ReportView.vue") }
  ]
});

router.beforeEach((to) => {
  const token = localStorage.getItem("workbench.token");
  if (!token && !to.meta.public) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (token && to.name === "login") {
    return { name: "dashboard" };
  }
  return true;
});
