<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>
        <el-button link type="primary" @click="goBack">← 返回项目</el-button>
        看板 · 项目 {{ projectId }}
      </span>
      <span>
        <el-input v-model="newColumnName" placeholder="新列名" size="small" style="width: 130px" />
        <el-button size="small" @click="addColumn">加列</el-button>
      </span>
    </el-header>
    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else class="board">
        <div
          v-for="group in columns"
          :key="group.column.id"
          class="column"
          @dragover.prevent
          @drop="onDrop(group.column.id)"
        >
          <div class="column-head">
            <span>{{ group.column.name }}</span>
            <span class="column-count">{{ group.cards.length }}</span>
          </div>
          <div class="cards">
            <div
              v-for="card in group.cards"
              :key="card.id"
              class="card"
              draggable="true"
              @dragstart="onDragStart(card.id)"
              @click="openTask(card.id)"
            >
              <div class="card-name">{{ card.name }}</div>
              <div class="card-meta">
                优先级 {{ card.pLevel }}<span v-if="card.ownerUserid"> · 负责人 {{ card.ownerUserid }}</span>
              </div>
            </div>
            <div v-if="group.cards.length === 0" class="column-empty">拖到这里</div>
          </div>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { BoardColumnWithCards } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));
const columns = ref<BoardColumnWithCards[]>([]);
const loading = ref(false);
const newColumnName = ref("");
const draggingTaskId = ref(0);

async function load(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    const board = await client.board(projectId.value);
    columns.value = board.columns;
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载看板失败");
  } finally {
    loading.value = false;
  }
}

function onDragStart(taskId: number): void {
  draggingTaskId.value = taskId;
}

async function onDrop(columnId: number): Promise<void> {
  if (!draggingTaskId.value) return;
  const taskId = draggingTaskId.value;
  draggingTaskId.value = 0;
  try {
    await client.moveTask(taskId, columnId);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "移动失败");
  }
}

async function addColumn(): Promise<void> {
  if (!newColumnName.value.trim()) return;
  try {
    await client.createColumn(projectId.value, newColumnName.value.trim());
    newColumnName.value = "";
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加列失败");
  }
}

async function openTask(taskId: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId: projectId.value }, query: { taskId } });
}

async function goBack(): Promise<void> {
  await router.push({ name: "projects" });
}

onMounted(load);
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}
.board {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  overflow-x: auto;
}
.column {
  width: 260px;
  flex: 0 0 260px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 10px;
  min-height: 200px;
}
.column-head {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 8px;
}
.column-count {
  color: #909399;
  font-weight: 400;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 60px;
}
.card {
  background: #fff;
  border-radius: 6px;
  padding: 10px;
  cursor: grab;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.card-name {
  font-size: 14px;
}
.card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.column-empty {
  color: #c0c4cc;
  font-size: 12px;
  text-align: center;
  padding: 12px 0;
}
</style>
