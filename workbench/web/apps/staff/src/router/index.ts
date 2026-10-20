import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import DashboardView from "../views/DashboardView.vue";
import ProjectsView from "../views/ProjectsView.vue";
import TasksView from "../views/TasksView.vue";
import PlanningView from "../views/PlanningView.vue";
import KanbanView from "../views/KanbanView.vue";
import GanttView from "../views/GanttView.vue";
import DailyReportView from "../views/DailyReportView.vue";
import ReportView from "../views/ReportView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    { path: "/", name: "dashboard", component: DashboardView },
    { path: "/projects", name: "projects", component: ProjectsView },
    { path: "/reports", name: "reports", component: DailyReportView },
    { path: "/projects/:projectId/tasks", name: "tasks", component: TasksView },
    { path: "/projects/:projectId/planning", name: "planning", component: PlanningView },
    { path: "/projects/:projectId/board", name: "board", component: KanbanView },
    { path: "/projects/:projectId/gantt", name: "gantt", component: GanttView },
    { path: "/projects/:projectId/report", name: "report", component: ReportView }
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
