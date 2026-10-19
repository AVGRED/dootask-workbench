import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import HomeView from "../views/HomeView.vue";
import ProjectsView from "../views/ProjectsView.vue";
import ProfileView from "../views/ProfileView.vue";
import TasksView from "../views/TasksView.vue";
import ReportView from "../views/ReportView.vue";
import MessagesView from "../views/MessagesView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    { path: "/", name: "home", component: HomeView },
    { path: "/projects", name: "projects", component: ProjectsView },
    { path: "/projects/:projectId/tasks", name: "tasks", component: TasksView },
    { path: "/report", name: "report", component: ReportView },
    { path: "/messages", name: "messages", component: MessagesView },
    { path: "/profile", name: "profile", component: ProfileView }
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
