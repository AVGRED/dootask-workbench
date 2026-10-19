<template>
  <el-container class="shell">
    <el-header class="shell-header">
      <div class="left">
        <span class="brand">工作台 · 管理端</span>
        <nav class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab.name"
            class="tab"
            :class="{ active: route.name === tab.name }"
            @click="go(tab.name)"
          >
            {{ tab.label }}
          </button>
        </nav>
      </div>
      <div class="right">
        <el-button link type="primary" @click="go('users')">用户管理</el-button>
        <el-button link type="primary" @click="go('settings')">设置</el-button>
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

const route = useRoute();
const router = useRouter();

const tabs = [
  { name: "team", label: "状态墙" },
  { name: "projects", label: "项目总览" },
  { name: "reports", label: "每日日报" }
];

async function go(name: string): Promise<void> {
  await router.push({ name });
}

async function logout(): Promise<void> {
  localStorage.removeItem("workbench.admin.token");
  localStorage.removeItem("workbench.admin.user");
  await router.replace({ name: "login" });
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}
.shell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}
.left {
  display: flex;
  align-items: center;
  gap: 24px;
}
.brand {
  font-size: 17px;
  font-weight: 700;
}
.tabs {
  display: flex;
  gap: 4px;
}
.tab {
  border: none;
  background: transparent;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
}
.tab:hover {
  background: #f5f7fa;
}
.tab.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}
.right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.shell-main {
  background: #f7f8fa;
}
</style>
