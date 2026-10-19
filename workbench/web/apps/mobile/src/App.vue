<template>
  <div class="app">
    <header class="topbar">
      <span class="title">{{ title }}</span>
      <span v-if="auth.isLoggedIn" class="who">{{ auth.user?.nickname ?? "" }}</span>
    </header>
    <main class="content">
      <router-view />
    </main>
    <nav v-if="auth.isLoggedIn" class="tabbar">
      <router-link v-for="tab in tabs" :key="tab.name" :to="tab.path" class="tab" :class="{ active: isActive(tab) }">
        <span class="tab-icon">{{ tab.icon }}</span>
        <span class="tab-label">{{ tab.label }}</span>
        <span v-if="tab.name === 'messages' && unread > 0" class="tab-badge">{{ unread }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { useAuthStore, client } from "./stores/auth";

const auth = useAuthStore();
const route = useRoute();

const unread = ref(0);

const tabs = [
  { name: "home", path: "/", label: "首页", icon: "🏠" },
  { name: "projects", path: "/projects", label: "项目", icon: "📁" },
  { name: "report", path: "/report", label: "日报", icon: "📝" },
  { name: "profile", path: "/profile", label: "我的", icon: "👤" }
];

const TITLES: Record<string, string> = {
  home: "工作台",
  projects: "我的项目",
  tasks: "任务",
  report: "每日日报",
  profile: "我的",
  messages: "消息"
};

const title = computed(() => TITLES[String(route.name)] ?? "工作台");

function isActive(tab: { name: string; path: string }): boolean {
  if (tab.name === "home") return route.name === "home";
  if (tab.name === "projects") return route.name === "projects" || route.name === "tasks";
  return route.name === tab.name;
}

onMounted(async () => {
  if (!auth.isLoggedIn) return;
  try {
    unread.value = await client.unreadCount();
  } catch {
    unread.value = 0;
  }
});


</script>
