import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import ProjectsView from "../views/ProjectsView.vue";
import TasksView from "../views/TasksView.vue";
import ReportView from "../views/ReportView.vue";
import MessagesView from "../views/MessagesView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    { path: "/", name: "home", component: ProjectsView },
    { path: "/projects/:projectId/tasks", name: "tasks", component: TasksView },
    { path: "/report", name: "report", component: ReportView },
    { path: "/messages", name: "messages", component: MessagesView }
  ]
});

router.beforeEach((to) => {
  const token = localStorage.getItem("workbench.token");
  if (!token && !to.meta.public) {
    return { name: "login" };
  }
  if (token && to.name === "login") {
    return { name: "home" };
  }
  return true;
});
