<template>
  <div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="projects.length === 0" class="empty">还没有项目</div>
    <div
      v-for="item in projects"
      :key="item.id"
      class="card"
      @click="openProject(item.id)"
    >
      <div class="card-title">{{ item.name }}</div>
      <div class="card-meta">
        成员 {{ item.memberCount }} 人<span v-if="item.archived"> · 已归档</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { ProjectView } from "@workbench/api";
import { client } from "../stores/auth";

const router = useRouter();
const projects = ref<ProjectView[]>([]);
const loading = ref(false);
const error = ref("");

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await client.listProjects({ page: 1, size: 50 });
    projects.value = result.list;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function openProject(id: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId: id } });
}

onMounted(load);
</script>
