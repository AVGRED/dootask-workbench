<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="brand-panel">
        <div class="brand-mark">
          <span class="brand-dot"></span>
          <span class="brand-name">工作台</span>
        </div>
        <h1 class="brand-title">把项目、任务和日报<br />收进一块可自由编排的画布</h1>
        <ul class="brand-points">
          <li><span class="point-icon">◧</span>看板 / 甘特 / 报表，一套数据三种视角</li>
          <li><span class="point-icon">◍</span>日报与工时自动汇总，管理者一眼看清进度</li>
          <li><span class="point-icon">✦</span>板块可拖拽编排，按自己的习惯排布</li>
        </ul>
        <div class="brand-foot">深色模式 · 移动端同步 · 角色权限</div>
      </aside>

      <main class="form-panel">
        <div class="form-head">
          <h2>登录</h2>
          <p>使用工作台账号继续</p>
        </div>

        <el-form :model="form" @submit.prevent="submit">
          <label class="field">
            <span class="field-label">账号</span>
            <el-input
              v-model="form.username"
              size="large"
              placeholder="邮箱或账号"
              autocomplete="username"
                          />
          </label>

          <label class="field">
            <span class="field-label">密码</span>
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              show-password
              autocomplete="current-password"
                            @keyup.enter="submit"
            />
          </label>

          <div class="form-extra">
            <el-checkbox v-model="remember">记住账号</el-checkbox>
          </div>

          <transition name="fade">
            <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-error" />
          </transition>

          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">
            登录
          </el-button>
        </el-form>

        <div class="form-foot">
          <span>忘记密码请联系管理员重置</span>
          <button class="theme-toggle" type="button" @click="toggleTheme">
            {{ theme === "dark" ? "☀️ 亮色" : "🌙 暗色" }}
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { useTheme } from "../composables/useTheme";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const { theme, toggleTheme } = useTheme();

const REMEMBER_KEY = "workbench.rememberedUser";
const form = reactive({ username: "", password: "" });
const remember = ref(true);
const loading = ref(false);
const error = ref("");

function initRemembered(): void {
  const saved = localStorage.getItem(REMEMBER_KEY);
  if (saved) {
    form.username = saved;
    remember.value = true;
  }
}

function persistRemembered(): void {
  if (remember.value) {
    localStorage.setItem(REMEMBER_KEY, form.username);
  } else {
    localStorage.removeItem(REMEMBER_KEY);
  }
}

async function submit(): Promise<void> {
  if (!form.username || !form.password) {
    error.value = "请输入账号与密码";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    await auth.login({ username: form.username, password: form.password });
    persistRemembered();
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    await router.replace(redirect);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "登录失败";
  } finally {
    loading.value = false;
  }
}
onMounted(initRemembered);
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(1200px 600px at 12% -10%, color-mix(in srgb, var(--wb-brand-600) 16%, transparent), transparent 60%),
    radial-gradient(900px 500px at 90% 110%, color-mix(in srgb, var(--wb-brand-400) 18%, transparent), transparent 60%),
    var(--wb-bg);
}
.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(340px, 0.95fr);
  width: min(980px, 100%);
  min-height: 520px;
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: 22px;
  box-shadow: var(--wb-shadow-2);
  overflow: hidden;
}

.brand-panel {
  position: relative;
  padding: 40px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: #fff;
  background: linear-gradient(150deg, var(--wb-brand-700), var(--wb-brand-500) 55%, var(--wb-brand-400));
  overflow: hidden;
}
.brand-panel::after {
  content: "";
  position: absolute;
  right: -90px;
  bottom: -120px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.28), transparent 65%);
}
.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.brand-dot {
  width: 12px;
  height: 12px;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.18);
}
.brand-title {
  margin: 8px 0 0;
  font-size: 26px;
  line-height: 1.45;
  font-weight: 700;
}
.brand-points {
  list-style: none;
  padding: 0;
  margin: 6px 0 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13.5px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.88);
}
.brand-points li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.point-icon {
  flex: 0 0 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 11px;
}
.brand-foot {
  margin-top: auto;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
  letter-spacing: 0.4px;
}

.form-panel {
  padding: 44px 40px 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 22px;
}
.form-head h2 {
  margin: 0;
  font-size: 22px;
  color: var(--wb-text);
}
.form-head p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--wb-text-3);
}
.field {
  display: block;
  margin-bottom: 16px;
}
.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--wb-text-2);
}
.form-error {
  margin-bottom: 14px;
}
.submit {
  width: 100%;
}
.form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.theme-toggle {
  border: none;
  background: none;
  color: var(--wb-brand-600);
  font-size: 12.5px;
  cursor: pointer;
  padding: 4px;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 160ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
@media (max-width: 860px) {
  .login-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }
  .brand-panel {
    padding: 28px;
  }
  .brand-title {
    font-size: 20px;
  }
  .brand-points,
  .brand-foot {
    display: none;
  }
  .form-panel {
    padding: 28px;
  }
}
.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -4px 0 12px;
}
</style>
