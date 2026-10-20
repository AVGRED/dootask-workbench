<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="brand-panel">
        <div class="brand-mark">
          <span class="brand-dot"></span>
          <span class="brand-name">星图 · 管理端</span>
        </div>
        <h1 class="brand-title">看清每个人在做什么<br />也看清拼图落在星图哪一格</h1>
        <div class="showcase">
          <figure class="shot shot-back">
            <img :src="shots.projects" alt="项目总览" />
            <figcaption>项目总览 · 完成度与逾期</figcaption>
          </figure>
          <figure class="shot shot-front">
            <img :src="shots.team" alt="状态墙" />
            <figcaption>状态墙 · 谁在做什么</figcaption>
          </figure>
        </div>

        <div class="brand-foot">仅管理员可访问 · 操作留痕</div>
      </aside>

      <main class="form-panel">
        <div class="form-head">
          <h2>管理端登录</h2>
          <p>请使用管理员账号登录</p>
        </div>

        <el-form :model="form" @submit.prevent="submit">
          <label class="field">
            <span class="field-label">账号</span>
            <span v-if="fieldError.username" class="field-error">{{ fieldError.username }}</span>
            <el-input v-model="form.username" size="large" placeholder="邮箱或账号" autocomplete="username" />
          </label>

          <label class="field">
            <span class="field-label">密码</span>
            <span v-if="fieldError.password" class="field-error">{{ fieldError.password }}</span>
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              show-password
              autocomplete="current-password"
              @keyup="onPasswordKeyup"
              @keyup.enter="submit"
            />
          </label>

          <div class="form-extra">
            <el-checkbox v-model="remember">记住账号</el-checkbox>
          </div>

          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-error" :class="{ shake: shaking }" />

          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">登录</el-button>
        </el-form>

        <div class="form-foot">
          <span>非管理员请前往员工端登录</span>
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
import teamShot from "../assets/showcase/team-dark.png";
import projectsShot from "../assets/showcase/projects-dark.png";

const shots = { team: teamShot, projects: projectsShot };

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const { theme, toggleTheme } = useTheme();

const REMEMBER_KEY = "workbench.rememberedUser";
const form = reactive({ username: "", password: "" });
const remember = ref(true);
const loading = ref(false);
const error = ref("");
const fieldError = reactive<{ username: string; password: string }>({ username: "", password: "" });
const capsLock = ref(false);
const shaking = ref(false);

function onPasswordKeyup(event: KeyboardEvent): void {
  capsLock.value = Boolean(event.getModifierState?.("CapsLock"));
}

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

function triggerShake(): void {
  shaking.value = true;
  window.setTimeout(() => {
    shaking.value = false;
  }, 400);
}

async function submit(): Promise<void> {
  fieldError.username = form.username ? "" : "请输入账号";
  fieldError.password = form.password ? "" : "请输入密码";
  if (fieldError.username || fieldError.password) {
    error.value = "请输入账号与密码";
    triggerShake();
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
    triggerShake();
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
    radial-gradient(1100px 560px at 88% -10%, color-mix(in srgb, var(--wb-brand-600) 14%, transparent), transparent 60%),
    radial-gradient(900px 500px at 8% 110%, color-mix(in srgb, var(--wb-brand-400) 16%, transparent), transparent 60%),
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
  background: linear-gradient(155deg, #1b1d2b, #262a44 45%, var(--wb-brand-700));
  overflow: hidden;
}
.brand-panel::after {
  content: "";
  position: absolute;
  right: -100px;
  bottom: -130px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--wb-brand-400) 45%, transparent), transparent 65%);
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
  background: var(--wb-brand-400);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--wb-brand-400) 22%, transparent);
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
  color: rgba(255, 255, 255, 0.86);
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
  background: rgba(255, 255, 255, 0.14);
  font-size: 11px;
}
.brand-foot {
  margin-top: auto;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
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
.showcase {
  position: relative;
  flex: 0 0 auto;
  height: 268px;
  margin-top: 4px;
}
.shot {
  position: absolute;
  width: 62%;
  aspect-ratio: 16 / 9;
  margin: 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.26);
  box-shadow: 0 16px 32px rgba(8, 10, 26, 0.42);
  background: rgba(255, 255, 255, 0.06);
  transition: transform var(--wb-duration) var(--wb-ease);
}
.shot img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top left;
}
.shot figcaption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 6px 10px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.92);
  background: linear-gradient(transparent, rgba(6, 8, 22, 0.78));
}
.shot-back {
  top: 6px;
  left: 0;
  transform: rotate(-4deg);
  opacity: 0.82;
}

.shot-front {
  top: 92px;
  left: 26%;
  transform: rotate(1.6deg);
}
.shot-front:hover {
  transform: rotate(0deg) translateY(-4px);
}
.field-error {
  margin-left: 8px;
  font-size: 12px;
  color: var(--wb-danger);
}
.caps-hint {
  margin: 0 0 10px;
  font-size: 12.5px;
  color: var(--wb-warning);
}
.shake {
  animation: wb-shake 320ms ease;
}
@keyframes wb-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  50% { transform: translateX(4px); }
  75% { transform: translateX(-2px); }
}
.brand-dot {
  border-radius: 0;
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
}
</style>
