<template>
  <div>
    <div v-if="error" class="error">{{ error }}</div>
    <div class="row" style="margin-bottom: 10px">
      <button class="ghost" type="button" @click="readAll">全部已读</button>
      <span class="card-meta" style="margin: 0">未读 {{ unread }}</span>
    </div>
    <div v-if="notifications.length === 0" class="empty">没有消息</div>
    <div v-for="item in notifications" :key="item.id" class="card">
      <div class="card-title">{{ item.title }}</div>
      <div class="card-meta">{{ item.createdAt }}<span v-if="!item.read"> · 未读</span></div>
      <button v-if="!item.read" class="ghost" type="button" style="margin-top: 8px" @click="readOne(item.id)">
        标为已读
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { NotificationView } from "@workbench/api";
import { client } from "../stores/auth";

const notifications = ref<NotificationView[]>([]);
const unread = ref(0);
const error = ref("");

async function load(): Promise<void> {
  try {
    notifications.value = await client.listNotifications();
    unread.value = await client.unreadCount();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  }
}

async function readOne(id: number): Promise<void> {
  try {
    await client.markNotificationRead(id);
    await load();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "操作失败";
  }
}

async function readAll(): Promise<void> {
  try {
    await client.markAllNotificationsRead();
    await load();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "操作失败";
  }
}

onMounted(load);
</script>
