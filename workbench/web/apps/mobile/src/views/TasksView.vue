<template>
  <div>
    <button class="ghost" type="button" @click="back">← 返回项目</button>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="tasks.length === 0" class="empty">没有待办任务</div>
    <div v-for="item in tasks" :key="item.id" class="card">
      <div class="card-title">{{ item.name }}</div>
      <div class="card-meta">优先级 {{ item.pLevel }}<span v-if="item.milestoneId"> · 里程碑 {{ item.milestoneId }}</span></div>
      <div class="row" style="margin-top: 10px">
        <button class="ghost" type="button" @click="complete(item.id)">完成</button>
        <button class="ghost" type="button" @click="openLog(item)">登记工时</button>
      </div>
    </div>

    <div v-if="logVisible" class="card">
      <div class="card-title">登记工时</div>
      <label class="field">
        <span>分钟</span>
        <input v-model.number="minutes" type="number" min="1" max="1440" />
      </label>
      <label class="field">
        <span>日期</span>
        <input v-model="workDate" type="date" />
      </label>
      <div class="row">
        <button class="primary" type="button" :disabled="saving" @click="submitLog">保存</button>
        <button class="ghost" type="button" @click="logVisible = false">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { TaskView } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));
const tasks = ref<TaskView[]>([]);
const loading = ref(false);
const error = ref("");
const logVisible = ref(false);
const logTaskId = ref(0);
const minutes = ref(60);
const workDate = ref(new Date().toISOString().slice(0, 10));
const saving = ref(false);

async function load(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    const result = await client.listTasks(projectId.value, { page: 1, size: 50 });
    tasks.value = result.list;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function complete(taskId: number): Promise<void> {
  try {
    await client.completeTask(taskId);
    await load();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "操作失败";
  }
}

function openLog(task: TaskView): void {
  logTaskId.value = task.id;
  minutes.value = 60;
  workDate.value = new Date().toISOString().slice(0, 10);
  logVisible.value = true;
}

async function submitLog(): Promise<void> {
  saving.value = true;
  try {
    await client.createWorklog(logTaskId.value, {
      requestId: `m-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      minutes: minutes.value,
      workDate: workDate.value
    });
    logVisible.value = false;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "登记失败";
  } finally {
    saving.value = false;
  }
}

async function back(): Promise<void> {
  await router.push({ name: "home" });
}

onMounted(load);
</script>
