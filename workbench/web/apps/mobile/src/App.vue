<template>
  <div class="app">
    <header class="topbar">
      <span class="title">工作台</span>
      <span v-if="auth.isLoggedIn" class="who">{{ auth.user?.nickname ?? "" }}</span>
    </header>
    <main class="content">
      <router-view />
    </main>
    <nav v-if="auth.isLoggedIn" class="tabbar">
      <router-link to="/" class="tab" :class="{ active: route.name === 'home' }">项目</router-link>
      <router-link to="/report" class="tab" :class="{ active: route.name === 'report' }">日报</router-link>
      <router-link to="/messages" class="tab" :class="{ active: route.name === 'messages' }">消息</router-link>
      <button class="tab" type="button" @click="logout">退出</button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "./stores/auth";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

async function logout(): Promise<void> {
  auth.logout();
  await router.replace({ name: "login" });
}
</script>
