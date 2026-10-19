<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>工作台登录</h2>
      <el-form :model="form" label-width="72px" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="邮箱" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-button type="primary" native-type="submit" :loading="loading" class="login-submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const form = reactive({ username: "", password: "" });
const loading = ref(false);
const error = ref("");

async function submit(): Promise<void> {
  if (!form.username || !form.password) {
    error.value = "请输入账号与密码";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    await auth.login({ username: form.username, password: form.password });
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    await router.replace(redirect);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "登录失败";
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f5f7fa;
}
.login-card {
  width: 360px;
}
.login-submit {
  width: 100%;
}
</style>
