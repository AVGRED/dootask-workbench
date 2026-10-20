<template>
  <el-container class="home">
    <el-header class="home-header">
      <span>星图 · 员工端</span>
      <span>
        {{ auth.user?.nickname || auth.user?.username }}
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </el-header>
    <el-main>
      <el-empty description="板块 2 起在这里接入项目与任务" />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();

async function logout(): Promise<void> {
  auth.logout();
  await router.replace({ name: "login" });
}
</script>

<style scoped>
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--wb-border);
}
</style>
