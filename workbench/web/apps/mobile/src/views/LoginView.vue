<template>
  <div class="login">
    <h2>登录工作台</h2>
    <div v-if="error" class="error">{{ error }}</div>
    <label class="field">
      <span>邮箱</span>
      <input v-model="username" type="email" autocomplete="username" placeholder="you@example.com" />
    </label>
    <label class="field">
      <span>密码</span>
      <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
    </label>
    <button class="primary" :disabled="loading" @click="submit">{{ loading ? "登录中…" : "登录" }}</button>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const username = ref("demo@workbench.local");
const password = ref("");
const error = ref("");
const loading = ref(false);

async function submit(): Promise<void> {
  error.value = "";
  loading.value = true;
  try {
    await auth.login({ username: username.value, password: password.value });
    await router.replace({ name: "home" });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "登录失败";
  } finally {
    loading.value = false;
  }
}
</script>
