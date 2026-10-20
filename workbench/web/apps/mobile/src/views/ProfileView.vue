<template>
  <div>
    <div class="profile-head">
      <img v-if="avatar" class="avatar" :src="`/api/v1/avatars/${avatar}`" alt="头像" />
      <div v-else class="avatar placeholder">{{ (auth.user?.nickname || "?").slice(0, 1) }}</div>
      <div class="who">
        <div class="name">{{ auth.user?.nickname || "未命名" }}</div>
        <div class="email">#{{ auth.user?.userid }}</div>
      </div>
      <button class="ghost" type="button" @click="pickAvatar">换头像</button>
      <input ref="fileInput" type="file" accept="image/*" hidden @change="upload" />
    </div>

    <div class="card">
      <div class="row between">
        <span class="card-title">我的状态</span>
        <span class="muted">{{ statusText }}</span>
      </div>
      <div class="status-list">
        <button
          v-for="item in STATUS"
          :key="item.value"
          class="status-item"
          :class="{ active: status === item.value }"
          type="button"
          @click="changeStatus(item.value)"
        >
          {{ item.emoji }} {{ item.label }}
        </button>
      </div>
    </div>

    <div class="card">
      <div class="row between">
        <span class="card-title">外观</span>
        <button class="ghost small" type="button" @click="toggleTheme">
          {{ theme === "dark" ? "☀️ 亮色" : "🌙 暗色" }}
        </button>
      </div>
      <div class="muted">当前：{{ theme === "dark" ? "暗色" : "亮色" }} · 选择会记住</div>
    </div>

    <div class="card">
      <div class="row between">
        <span class="card-title">通知</span>
        <button v-if="notifications.length > 0" class="ghost small" type="button" @click="readAll">全部已读</button>
      </div>
      <div v-if="notifications.length === 0" class="empty">没有未读通知</div>
      <div v-for="note in notifications" :key="note.id" class="note" @click="read(note.id)">
        <div class="note-title">{{ note.title }}</div>
        <div class="card-meta">{{ (note.createdAt || "").slice(0, 16).replace("T", " ") }}</div>
      </div>
    </div>

    <button class="primary logout" type="button" @click="logout">退出登录</button>
    <div v-if="error" class="error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { NotificationView } from "@workbench/api";
import { client } from "../stores/auth";
import { useTheme } from "../composables/useTheme";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const avatar = ref("");
const status = ref("idle");
const notifications = ref<NotificationView[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);
const error = ref("");

const STATUS = [
  { value: "focus", label: "专注", emoji: "🎯" },
  { value: "meeting", label: "会议", emoji: "🗣" },
  { value: "away", label: "外出", emoji: "🚶" },
  { value: "leave", label: "请假", emoji: "🌴" },
  { value: "idle", label: "空闲", emoji: "☕" }
];

const statusText = computed(() => STATUS.find((item) => item.value === status.value)?.label ?? status.value);

function pickAvatar(): void {
  fileInput.value?.click();
}

async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    const result = await client.uploadAvatar(file);
    avatar.value = result.avatar;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "上传失败";
  } finally {
    input.value = "";
  }
}

async function changeStatus(value: string): Promise<void> {
  try {
    await client.setStatus(value);
    status.value = value;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "设置状态失败";
  }
}

async function load(): Promise<void> {
  try {
    avatar.value = (await client.myAvatar()).avatar;
    status.value = (await client.myStatus()).status;
    notifications.value = await client.listNotifications(true);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  }
}

async function read(id: number): Promise<void> {
  await client.markNotificationRead(id);
  notifications.value = notifications.value.filter((item) => item.id !== id);
}

async function readAll(): Promise<void> {
  await client.markAllNotificationsRead();
  notifications.value = [];
}

async function logout(): Promise<void> {
  await client.logout().catch(() => undefined);
  auth.logout();
  await router.replace({ name: "login" });
}

const { theme, toggleTheme } = useTheme();

onMounted(load);
</script>

<style scoped>
.profile-head {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 12px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--wb-brand-50);
  color: var(--wb-brand-600);
  font-size: 22px;
  font-weight: 700;
}
.who {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 17px;
  font-weight: 700;
}
.email {
  font-size: 12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
}
.status-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.status-item {
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--wb-surface-2);
  font-size: 13px;
}
.status-item.active {
  background: var(--wb-brand-50);
  border-color: var(--wb-brand-600);
  color: var(--wb-brand-600);
  font-weight: 600;
}
.note {
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}
.note-title {
  font-size: 14px;
}
.between {
  justify-content: space-between;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.small {
  font-size: 12px;
  padding: 4px 10px;
}
.logout {
  margin-top: 8px;
  background: var(--wb-danger);
}
</style>
