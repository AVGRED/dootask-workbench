<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">看板 · 项目 {{ projectId }}</span>
      <span class="page-actions"><span>
        <el-input v-model="newColumnName" placeholder="新列名" size="small" style="width: 130px" />
        <el-button size="small" @click="addColumn">加列</el-button>
      </span></span>
    </el-header>
    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else class="board">
        <section
          v-for="group in columns"
          :key="group.column.id"
          class="column"
          :class="{ 'drop-target': dragOverColumn === group.column.id }"
          @dragover.prevent="dragOverColumn = group.column.id"
          @dragleave="dragOverColumn = dragOverColumn === group.column.id ? 0 : dragOverColumn"
          @drop="onDrop(group.column.id)"
        >
          <header class="column-head">
            <span class="column-name">{{ group.column.name }}</span>
            <span class="column-count">{{ group.cards.length }}</span>
          </header>

          <div class="cards">
            <article
              v-for="card in group.cards"
              :key="card.id"
              class="card"
              :class="{ dragging: draggingTaskId === card.id, done: card.completed }"
              draggable="true"
              @dragstart="onDragStart(card.id)"
              @dragend="onDragEnd"
              @click="openTask(card.id)"
            >
              <div class="card-name">{{ card.name }}</div>
              <p v-if="card.desc" class="card-desc">{{ card.desc }}</p>
              <footer class="card-foot">
                <span class="p-level" :class="'p' + card.pLevel">P{{ card.pLevel }}</span>
                <span v-if="card.ownerUserid" class="owner">#{{ card.ownerUserid }}</span>
                <span v-if="card.completed" class="done-tag">已完成</span>
              </footer>
            </article>
            <div v-if="group.cards.length === 0" class="column-empty">拖到这里</div>
          </div>
        </section>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { BoardColumnWithCards } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));
const columns = ref<BoardColumnWithCards[]>([]);
const loading = ref(false);
const newColumnName = ref("");
const draggingTaskId = ref(0);
const dragOverColumn = ref(0);

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

function onDragEnd(): void {
  draggingTaskId.value = 0;
  dragOverColumn.value = 0;
}

async function onDrop(columnId: number): Promise<void> {
  if (!draggingTaskId.value) return;
  const taskId = draggingTaskId.value;
  draggingTaskId.value = 0;
  dragOverColumn.value = 0;
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

onMounted(load);
</script>

<style scoped>
.board {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  overflow-x: auto;
  padding-bottom: 8px;
}
.column {
  width: 280px;
  flex: 0 0 280px;
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  padding: 12px;
  min-height: 220px;
  transition: border-color var(--wb-duration) var(--wb-ease),
    background var(--wb-duration) var(--wb-ease);
}
.column.drop-target {
  border-color: var(--wb-brand-400);
  background: color-mix(in srgb, var(--wb-brand-600) 6%, var(--wb-surface-2));
}
.column-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 2px;
}
.column-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--wb-text);
}
.column-head::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--wb-brand-500);
  margin-right: 8px;
}
.column-count {
  margin-left: auto;
  min-width: 22px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  color: var(--wb-text-3);
  font-size: 11.5px;
  text-align: center;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
}
.card {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius);
  padding: 10px 12px;
  cursor: grab;
  box-shadow: var(--wb-shadow-1);
  transition: box-shadow var(--wb-duration) var(--wb-ease),
    transform var(--wb-duration) var(--wb-ease),
    opacity var(--wb-duration) var(--wb-ease);
}
.card:hover {
  box-shadow: var(--wb-shadow-2);
  transform: translateY(-1px);
}
.card.dragging {
  opacity: 0.45;
  cursor: grabbing;
}
.card.done .card-name {
  color: var(--wb-text-3);
  text-decoration: line-through;
}
.card-name {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--wb-text);
  line-height: 1.4;
}
.card-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--wb-text-3);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.p-level {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 6px;
  background: var(--wb-surface-2);
  color: var(--wb-text-2);
}
.p-level.p0 { background: color-mix(in srgb, var(--wb-danger) 14%, transparent); color: var(--wb-danger); }
.p-level.p1 { background: color-mix(in srgb, var(--wb-warning) 16%, transparent); color: var(--wb-warning); }
.p-level.p2 { background: color-mix(in srgb, var(--wb-brand-600) 12%, transparent); color: var(--wb-brand-700); }
.owner {
  font-size: 11.5px;
  color: var(--wb-text-3);
}
.done-tag {
  margin-left: auto;
  font-size: 11px;
  color: var(--wb-success);
}
.column-empty {
  padding: 22px 0;
  border: 1px dashed var(--wb-border-strong);
  border-radius: var(--wb-radius);
  color: var(--wb-text-3);
  font-size: 12px;
  text-align: center;
}
</style>
