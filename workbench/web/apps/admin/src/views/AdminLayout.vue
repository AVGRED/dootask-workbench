<template>
  <el-container class="shell">
    <el-header class="shell-header">
      <div class="left">
        <span class="brand">星图 · 管理端</span>
        <nav class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab.name"
            class="tab"
            :class="{ active: route.name === tab.name }"
            @click="go(tab.name)"
          >
            <span class="tab-icon">{{ tab.icon }}</span>
            <span class="tab-label">{{ tab.label }}</span>
          </button>
        </nav>
      </div>
      <div class="right">
        <el-button link type="primary" @click="go('users')">用户管理</el-button>
        <el-button link type="primary" @click="go('settings')">设置</el-button>
        <el-button link @click="toggleTheme">{{ theme === "dark" ? "☀️" : "🌙" }}</el-button>
        <el-button link @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main class="shell-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { client } from "../stores/auth";
import { useTheme } from "../composables/useTheme";

const route = useRoute();
const router = useRouter();
const { theme, toggleTheme } = useTheme();

const tabs = [
  { name: "team", label: "状态墙", icon: "👥" },
  { name: "projects", label: "项目总览", icon: "📁" },
  { name: "reports", label: "每日日报", icon: "📝" }
];

async function go(name: string): Promise<void> {
  await router.push({ name });
}

async function logout(): Promise<void> {
  await client.logout().catch(() => undefined);
  localStorage.removeItem("workbench.admin.token");
  localStorage.removeItem("workbench.admin.user");
  await router.replace({ name: "login" });
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
  background: var(--wb-bg);
}
.shell-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 20px;
  background: color-mix(in srgb, var(--wb-surface) 82%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--wb-border);
}
.left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.brand {
  font-size: 16px;
  font-weight: 700;
  color: var(--wb-text);
  letter-spacing: 0.2px;
}
.brand::before {
  content: "";
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  margin-right: 8px;
  background: var(--wb-brand-600);
  transform: translateY(-1px);
}
.tabs {
  display: flex;
  gap: 2px;
  padding: 4px;
  border-radius: var(--wb-radius);
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
}
.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  padding: 7px 14px;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--wb-text-2);
  cursor: pointer;
  transition: background var(--wb-duration) var(--wb-ease),
    color var(--wb-duration) var(--wb-ease);
}
.tab:hover {
  color: var(--wb-text);
  background: color-mix(in srgb, var(--wb-brand-600) 8%, transparent);
}
.tab.active {
  background: var(--wb-brand-600);
  color: #fff;
  box-shadow: var(--wb-shadow-1);
}
.tab-icon {
  font-size: 13px;
  line-height: 1;
}
.right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.shell-main {
  padding: 20px;
  background: var(--wb-bg);
}
.brand::before {
  border-radius: 0;
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
}
</style>
