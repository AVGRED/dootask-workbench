<template>
  <header class="app-header">
    <div class="header-left">
      <button class="brand" type="button" @click="go('dashboard')">
        <span class="brand-dot"></span>星图
      </button>
      <nav class="nav">
        <router-link
          v-for="item in navs"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-item"
          :class="{ active: isActive(item) }"
        >
          {{ item.label }}
        </router-link>
      </nav>
    </div>

    <div class="header-right">
      <el-dropdown trigger="click" @command="onStatusCommand">
        <button class="status-pill" :class="status" type="button">
          <span class="status-dot"></span>{{ statusLabel }}
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="item in STATUSES" :key="item.value" :command="item.value">
              {{ item.emoji }} {{ item.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <button class="icon-btn notify-btn" type="button" title="消息" @click="openNotifications">
        🔔
        <span v-if="unread > 0" class="notify-dot">{{ unread > 99 ? "99+" : unread }}</span>
      </button>

      <el-tooltip :content="theme === 'dark' ? '切换到亮色' : '切换到暗色'" placement="bottom">
        <button class="icon-btn" type="button" @click="toggleTheme">
          {{ theme === "dark" ? "☀️" : "🌙" }}
        </button>
      </el-tooltip>

      <el-dropdown trigger="click" @command="onUserCommand">
        <button class="user" type="button">
          <img v-if="avatar" class="avatar" :src="`/api/v1/avatars/${avatar}`" alt="头像" />
          <span v-else class="avatar placeholder">{{ (nickname || "我").slice(0, 1) }}</span>
          <span class="nickname">{{ nickname || "未登录" }}</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="avatar">上传头像</el-dropdown-item>
            <el-dropdown-item command="report">我的日报</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <input ref="fileInput" type="file" accept="image/*" hidden @change="uploadAvatar" />
    </div>

  </header>

  <el-drawer v-model="notifyVisible" title="消息" size="420px">
    <div class="notify-actions">
      <el-button size="small" @click="readAllNotifications">全部已读</el-button>
      <span class="notify-unread">未读 {{ unread }}</span>
    </div>
    <el-empty v-if="notifications.length === 0" description="没有新消息" />
    <div v-for="item in notifications" :key="item.id" class="notify-item">
      <div class="notify-title">
        {{ item.title }}
        <el-tag v-if="!item.read" size="small" type="warning">未读</el-tag>
      </div>
      <div class="notify-meta">{{ (item.createdAt || "").replace("T", " ").slice(0, 16) }}</div>
      <p class="notify-body">{{ item.payload }}</p>
      <el-button v-if="!item.read" link type="primary" size="small" @click="markNotificationRead(item)">
        标为已读
      </el-button>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { client } from "../../stores/auth";
import { useTheme } from "../../composables/useTheme";

const notifyVisible = ref(false);
const notifications = ref<{ id: number; title: string; payload?: string; read: boolean; createdAt?: string }[]>([]);

const unread = computed(() => notifications.value.filter((item) => !item.read).length);

async function loadNotifications(): Promise<void> {
  try {
    notifications.value = await client.listNotifications();
  } catch {
    notifications.value = [];
  }
}

function openNotifications(): void {
  notifyVisible.value = true;
  void loadNotifications();
}

async function markNotificationRead(item: { id: number }): Promise<void> {
  try {
    await client.markNotificationRead(item.id);
    await loadNotifications();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

async function readAllNotifications(): Promise<void> {
  try {
    await client.markAllNotificationsRead();
    await loadNotifications();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

const route = useRoute();
const router = useRouter();
const { theme, toggleTheme } = useTheme();

const NAVS = [
  { name: "dashboard", label: "首页", aliases: ["dashboard"] },
  { name: "projects", label: "项目", aliases: ["projects", "tasks", "board", "gantt", "planning", "report"] },
  { name: "reports", label: "日报", aliases: ["reports"] },
  { name: "starmap", label: "星图", aliases: ["starmap"] },
  { name: "settings", label: "设置", aliases: ["settings"] }
];

const STATUSES = [
  { value: "focus", label: "专注", emoji: "🎯" },
  { value: "idle", label: "空闲", emoji: "☕" },
  { value: "meeting", label: "会议", emoji: "🗣" },
  { value: "away", label: "外出", emoji: "🚶" },
  { value: "leave", label: "请假", emoji: "🌴" }
];

const navs = NAVS.map((item) => ({ name: item.name, label: item.label }));
const status = ref("idle");
const avatar = ref("");
const nickname = ref("");
const fileInput = ref<HTMLInputElement | null>(null);

const statusLabel = computed(
  () => STATUSES.find((item) => item.value === status.value)?.label ?? "空闲"
);

function isActive(item: { name: string }): boolean {
  const def = NAVS.find((nav) => nav.name === item.name);
  const current = String(route.name ?? "");
  return def ? def.aliases.includes(current) : current === item.name;
}

async function go(name: string): Promise<void> {
  await router.push({ name });
}

async function onStatusCommand(value: string): Promise<void> {
  try {
    await client.setStatus(value);
    status.value = value;
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "设置状态失败");
  }
}

async function onUserCommand(command: string): Promise<void> {
  if (command === "avatar") {
    fileInput.value?.click();
  } else if (command === "report") {
    await router.push({ name: "reports" });
  } else if (command === "logout") {
    await client.logout().catch(() => undefined);
    localStorage.removeItem("workbench.token");
    localStorage.removeItem("workbench.user");
    await router.replace({ name: "login" });
  }
}

async function uploadAvatar(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    const result = await client.uploadAvatar(file);
    avatar.value = result.avatar;
    ElMessage.success("头像已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "头像上传失败");
  } finally {
    input.value = "";
  }
}

onMounted(async () => {
  const stored = JSON.parse(localStorage.getItem("workbench.user") ?? "{}") as { nickname?: string };
  nickname.value = stored.nickname ?? "";
  try {
    const [mine, avatarInfo] = await Promise.all([client.myStatus(), client.myAvatar()]);
    status.value = mine.status || "idle";
    avatar.value = avatarInfo.avatar;
  } catch {
    // 读取失败不影响导航
  }
});
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 20px;
  background: color-mix(in srgb, var(--wb-surface) 62%, transparent);
  backdrop-filter: blur(18px) saturate(120%);
  border-bottom: 1px solid var(--wb-border);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: none;
  padding: 0;
  font-family: var(--wb-font);
  font-size: 16px;
  font-weight: 700;
  color: var(--wb-text);
  cursor: pointer;
  letter-spacing: 0.2px;
}
.brand-dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  background: linear-gradient(135deg, var(--wb-brand-400), var(--wb-brand-700));
}
.nav {
  display: flex;
  align-items: center;
  gap: 4px;
}
.nav-item {
  position: relative;
  padding: 8px 12px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 500;
  color: var(--wb-text-2);
  text-decoration: none;
  transition: color var(--wb-duration) var(--wb-ease),
    background var(--wb-duration) var(--wb-ease);
}
.nav-item:hover {
  color: var(--wb-text);
  background: color-mix(in srgb, var(--wb-brand-600) 8%, transparent);
}
.nav-item.active {
  color: var(--wb-brand-600);
  background: var(--wb-brand-50);
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--wb-border);
  background: var(--wb-surface-2);
  font-size: 13px;
  color: var(--wb-text-2);
  cursor: pointer;
  transition: border-color var(--wb-duration) var(--wb-ease);
}
.status-pill:hover {
  border-color: var(--wb-border-strong);
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--wb-text-3);
}
.status-pill.focus .status-dot { background: var(--wb-brand-600); box-shadow: 0 0 0 3px var(--wb-brand-100); }
.status-pill.meeting .status-dot { background: var(--wb-warning); box-shadow: 0 0 0 3px color-mix(in srgb, var(--wb-warning) 18%, transparent); }
.status-pill.away .status-dot { background: var(--wb-success); box-shadow: 0 0 0 3px color-mix(in srgb, var(--wb-success) 18%, transparent); }
.status-pill.leave .status-dot { background: var(--wb-danger); box-shadow: 0 0 0 3px color-mix(in srgb, var(--wb-danger) 18%, transparent); }
.icon-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  cursor: pointer;
  font-size: 15px;
  transition: background var(--wb-duration) var(--wb-ease);
}
.icon-btn:hover {
  background: var(--wb-surface-2);
}
.user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  cursor: pointer;
}
.user:hover {
  border-color: var(--wb-border-strong);
}
.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar.placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--wb-brand-50);
  color: var(--wb-brand-700);
  font-size: 12px;
  font-weight: 600;
}
.nickname {
  font-size: 13px;
  color: var(--wb-text-2);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 720px) {
  .nickname {
    display: none;
  }
  .nav-item {
    padding: 8px;
  }
}
.brand-dot {
  border-radius: 0;
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
}
.notify-btn {
  position: relative;
}
.notify-dot {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}
.notify-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.notify-unread {
  font-size: 12px;
  color: var(--wb-text-3);
}
.notify-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--wb-border);
}
.notify-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-text);
}
.notify-meta {
  font-size: 12px;
  color: var(--wb-text-3);
  margin: 4px 0;
}
.notify-body {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--wb-text-2);
  white-space: pre-wrap;
}
</style>
