import { createRouter, createWebHistory } from "vue-router";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: () => import("../views/LoginView.vue"), meta: { public: true } },
    { path: "/", name: "home", component: () => import("../views/HomeView.vue") },
    { path: "/projects", name: "projects", component: () => import("../views/ProjectsView.vue") },
    { path: "/projects/:projectId/tasks", name: "tasks", component: () => import("../views/TasksView.vue") },
    { path: "/report", name: "report", component: () => import("../views/ReportView.vue") },
    { path: "/messages", name: "messages", component: () => import("../views/MessagesView.vue") },
    { path: "/profile", name: "profile", component: () => import("../views/ProfileView.vue") }
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
