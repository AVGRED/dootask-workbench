<template>
  <div class="login-page">
    <div class="hero">
      <div class="hero-mark">
        <span class="dot"></span>
        <span>星图</span>
      </div>
      <h1 class="hero-title">今天的事<br />汇入一张星图</h1>
      <p class="hero-sub">项目 · 任务 · 日报，随时随地看一眼</p>

      <figure class="hero-shot">
        <img :src="dashboardShot" alt="星图首页预览" />
      </figure>
    </div>

    <div class="sheet">
      <h2 class="sheet-title">登录</h2>
      <p class="sheet-sub">使用星图账号继续</p>

      <div v-if="error" class="error-banner" :class="{ shake: shaking }">
        <span class="error-icon">!</span>
        <span>{{ error }}</span>
      </div>

      <label class="field">
        <span class="field-label">账号</span>
        <input
          v-model="username"
          type="text"
          inputmode="email"
          autocapitalize="off"
          autocomplete="username"
          placeholder="邮箱或账号"
          @keyup.enter="submit"
        />
        <span v-if="fieldError === '请输入账号'" class="field-error">{{ fieldError }}</span>
      </label>

      <label class="field">
        <span class="field-label">密码</span>
        <p v-if="capsLock" class="caps-hint">⚠️ 大写锁定已开启</p>
        <div class="input-wrap">
          <input
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup="onPasswordKeyup"
            @keyup.enter="submit"
          />
          <button class="toggle" type="button" @click="showPassword = !showPassword">
            {{ showPassword ? "隐藏" : "显示" }}
          </button>
        </div>
        <span v-if="fieldError === '请输入密码'" class="field-error">{{ fieldError }}</span>
      </label>

      <button class="primary" type="button" :disabled="loading" @click="submit">
        <span v-if="loading" class="spinner"></span>
        {{ loading ? "登录中…" : "登录" }}
      </button>

      <div class="sheet-foot">
        <span>忘记密码请联系管理员</span>
        <button class="theme-toggle" type="button" @click="toggleTheme">
          {{ theme === "dark" ? "☀️ 亮色" : "🌙 暗色" }}
        </button>
      </div>
    </div>

    <p class="version">星图 · 移动端 v2</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { useTheme } from "../composables/useTheme";
import dashboardShot from "../assets/showcase/dashboard-dark.png";

const auth = useAuthStore();
const router = useRouter();
const { theme, toggleTheme } = useTheme();

const REMEMBER_KEY = "workbench.rememberedUser";
const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);
const showPassword = ref(false);
const fieldError = ref("");
const capsLock = ref(false);
const shaking = ref(false);

function onPasswordKeyup(event: KeyboardEvent): void {
  capsLock.value = Boolean(event.getModifierState?.("CapsLock"));
}

function triggerShake(): void {
  shaking.value = true;
  window.setTimeout(() => {
    shaking.value = false;
  }, 400);
}

async function submit(): Promise<void> {
  fieldError.value = "";
  if (!username.value || !password.value) {
    fieldError.value = !username.value ? "请输入账号" : "请输入密码";
    error.value = "请输入账号与密码";
    triggerShake();
    return;
  }
  error.value = "";
  loading.value = true;
  try {
    await auth.login({ username: username.value, password: password.value });
    localStorage.setItem(REMEMBER_KEY, username.value);
    await router.replace({ name: "home" });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "登录失败";
    triggerShake();
  } finally {
    loading.value = false;
  }
}
onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY);
  if (saved) username.value = saved;
});
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  margin: -14px -14px 0;
  display: flex;
  flex-direction: column;
  background: var(--wb-bg);
}

.hero {
  position: relative;
  padding: 28px 22px 54px;
  color: #fff;
  background: linear-gradient(160deg, #1c1e2e 0%, var(--wb-brand-700) 62%, var(--wb-brand-500) 100%);
  border-bottom-left-radius: 26px;
  border-bottom-right-radius: 26px;
  overflow: hidden;
}
.hero::after {
  content: "";
  position: absolute;
  right: -70px;
  top: -60px;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.22), transparent 65%);
}
.hero-mark {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.16);
}
.hero-title {
  margin: 16px 0 8px;
  font-size: 26px;
  line-height: 1.35;
  font-weight: 700;
}
.hero-sub {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.sheet {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  margin: -30px 14px 0;
  padding: 22px 18px 20px;
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-2);
}
.sheet-title {
  margin: 0;
  font-size: 19px;
  color: var(--wb-text);
}
.sheet-sub {
  margin: 4px 0 18px;
  font-size: 12.5px;
  color: var(--wb-text-3);
}

.error-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--wb-danger) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--wb-danger) 28%, transparent);
  color: var(--wb-danger);
  font-size: 13px;
}
.error-icon {
  flex: 0 0 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--wb-danger);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.field {
  display: block;
  margin-bottom: 14px;
}
.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 12.5px;
  color: var(--wb-text-2);
}
.input-wrap {
  position: relative;
}
.input-wrap input {
  padding-right: 62px;
}
.toggle {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: none;
  color: var(--wb-brand-600);
  font-size: 12.5px;
  padding: 6px;
  cursor: pointer;
}

.primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 13px;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--wb-brand-500), var(--wb-brand-700));
  border: none;
  border-radius: var(--wb-radius);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--wb-brand-600) 30%, transparent);
}
.primary:active {
  transform: translateY(1px);
}
.spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.sheet-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 12px;
  color: var(--wb-text-3);
}
.theme-toggle {
  border: none;
  background: none;
  color: var(--wb-brand-600);
  font-size: 12px;
  padding: 4px;
}

.version {
  margin: 14px 0 18px;
  text-align: center;
  font-size: 11px;
  color: var(--wb-text-3);
}
.hero-shot {
  position: relative;
  width: 64%;
  margin: 14px 0 -30px auto;
  aspect-ratio: 16 / 10;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 18px 30px rgba(6, 8, 22, 0.4);
  transform: rotate(-4deg);
}
.hero-shot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top left;
}
.field-error {
  display: block;
  margin-top: 4px;
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
.dot {
  border-radius: 0;
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
}
</style>
