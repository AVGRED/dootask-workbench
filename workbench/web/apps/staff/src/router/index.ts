import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import ProjectsView from "../views/ProjectsView.vue";
import TasksView from "../views/TasksView.vue";
import PlanningView from "../views/PlanningView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    { path: "/", name: "projects", component: ProjectsView },
    { path: "/projects/:projectId/tasks", name: "tasks", component: TasksView },
    { path: "/projects/:projectId/planning", name: "planning", component: PlanningView }
  ]
});

router.beforeEach((to) => {
  const token = localStorage.getItem("workbench.token");
  if (!token && !to.meta.public) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (token && to.name === "login") {
    return { name: "projects" };
  }
  return true;
});
