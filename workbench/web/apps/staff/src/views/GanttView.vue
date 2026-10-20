<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">甘特图 · 项目 {{ projectId }}</span>
      <span class="page-actions"><span v-if="gantt" class="range">{{ gantt.rangeStart }} ~ {{ gantt.rangeEnd }}</span></span>
    </el-header>
    <el-main class="page-body">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!gantt || gantt.tasks.length === 0" class="empty">还没有带开始/结束日期的任务</div>
      <div v-else class="panel">
        <div class="gantt-toolbar">
          <div class="legend">
            <span class="legend-item"><i class="dot brand"></i>进行中</span>
            <span class="legend-item"><i class="dot ok"></i>已完成</span>
            <span class="legend-item"><i class="dot muted"></i>依赖关系</span>
          </div>
          <div class="gantt-meta">
            {{ gantt.tasks.length }} 个任务 · 跨度 {{ gantt.rangeStart }} ~ {{ gantt.rangeEnd }}
          </div>
        </div>

        <div class="gantt" :style="{ minWidth: `${totalWidth}px` }">
        <div class="gantt-head">
          <div class="name-col">任务</div>
          <div class="timeline-head" :style="{ width: `${timelineWidth}px` }">
            <div v-if="todayLeft !== null" class="today-line" :style="{ left: `${todayLeft}px` }"></div>
            <div v-for="day in days" :key="day.key" class="day-tick" :style="{ left: `${day.left}px` }">
              <span v-if="day.label">{{ day.label }}</span>
            </div>
          </div>
        </div>

        <div v-for="row in rows" :key="row.task.id" class="gantt-row">
          <div class="name-col" :title="row.task.name">{{ row.task.name }}</div>
          <div class="timeline" :style="{ width: `${timelineWidth}px`, height: `${rowHeight}px` }">
            <div
              class="bar"
              :class="{ done: row.task.completed }"
              :style="{ left: `${row.left}px`, width: `${row.width}px` }"
              :title="`${row.task.startDate} ~ ${row.task.endDate}`"
              @click="openTask(row.task.id)"
            >
              {{ row.task.name }}
            </div>
          </div>
        </div>

        <svg
          class="edge-layer"
          :width="timelineWidth"
          :height="rows.length * rowHeight"
          :style="{ top: '30px', left: '200px' }"
        >
          <path
            v-for="edge in edgePaths"
            :key="`${edge.fromId}-${edge.toId}`"
            :d="edge.path"
            fill="none"
            stroke="var(--wb-text-3)"
            stroke-width="1.5"
            marker-end="url(#arrow)"
          />
        </svg>
        <svg width="0" height="0">
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--wb-border-strong)" />
            </marker>
          </defs>
        </svg>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { GanttTask, GanttView } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));
const gantt = ref<GanttView | null>(null);
const loading = ref(false);

const DAY_WIDTH = 28;
const ROW_HEIGHT = 34;
const rowHeight = ROW_HEIGHT;

function toDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function diffDays(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / 86_400_000);
}

const rangeStart = computed(() => (gantt.value ? toDate(gantt.value.rangeStart) : new Date()));
const totalDays = computed(() =>
  gantt.value ? diffDays(rangeStart.value, toDate(gantt.value.rangeEnd)) + 1 : 1
);
const timelineWidth = computed(() => Math.max(totalDays.value, 14) * DAY_WIDTH);
const totalWidth = computed(() => 200 + timelineWidth.value);

const days = computed(() => {
  const result: { key: string; left: number; label: string }[] = [];
  for (let i = 0; i < totalDays.value; i++) {
    const date = new Date(rangeStart.value.getTime() + i * 86_400_000);
    const label = i % 7 === 0 ? `${date.getMonth() + 1}/${date.getDate()}` : "";
    result.push({ key: `${date.getTime()}`, left: i * DAY_WIDTH, label });
  }
  return result;
});

interface EdgePath {
  fromId: number;
  toId: number;
  path: string;
}

interface Row {
  task: GanttTask;
  left: number;
  width: number;
}

const rows = computed<Row[]>(() => {
  if (!gantt.value) return [];
  return gantt.value.tasks.map((task) => {
    const start = diffDays(rangeStart.value, toDate(task.startDate));
    const span = diffDays(toDate(task.startDate), toDate(task.endDate)) + 1;
    return {
      task,
      left: Math.max(0, start) * DAY_WIDTH,
      width: Math.max(span, 1) * DAY_WIDTH
    };
  });
});

const edgePaths = computed<EdgePath[]>(() => {
  if (!gantt.value) return [];
  const rowsById = new Map(rows.value.map((row, index) => [row.task.id, { row, index }]));
  const result: EdgePath[] = [];
  for (const edge of gantt.value.edges ?? []) {
    const from = rowsById.get(edge.predecessorTaskId);
    const to = rowsById.get(edge.successorTaskId);
    if (!from || !to) continue;
    const fromY = from.index * ROW_HEIGHT + ROW_HEIGHT / 2;
    const toY = to.index * ROW_HEIGHT + ROW_HEIGHT / 2;
    const x1 = from.row.left + from.row.width;
    const x2 = to.row.left;
    const midX = x1 + Math.max(8, (x2 - x1) / 2);
    result.push({
      fromId: from.row.task.id,
      toId: to.row.task.id,
      path: `M ${x1} ${fromY} L ${midX} ${fromY} L ${midX} ${toY} L ${x2} ${toY}`
    });
  }
  return result;
});

async function load(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    gantt.value = await client.gantt(projectId.value);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载甘特图失败");
  } finally {
    loading.value = false;
  }
}

async function openTask(taskId: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId: projectId.value }, query: { taskId } });
}

const todayLeft = computed(() => {
  if (!gantt.value) return null;
  const start = new Date(gantt.value.rangeStart + "T00:00:00").getTime();
  const end = new Date(gantt.value.rangeEnd + "T00:00:00").getTime();
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  if (today < start || today > end) return null;
  return Math.round(((today - start) / (end - start)) * timelineWidth.value);
});

onMounted(load);
</script>

<style scoped>
.range {
  color: var(--wb-text-3);
  font-size: 13px;
}
.panel {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 14px;
}
.gantt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.legend {
  display: flex;
  gap: 16px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--wb-text-2);
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  display: inline-block;
}
.dot.brand { background: var(--wb-brand-600); }
.dot.ok { background: var(--wb-success); }
.dot.muted { background: var(--wb-border-strong); }
.gantt-meta {
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.gantt {
  position: relative;
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius);
  overflow: auto;
  background: var(--wb-surface);
}
.gantt-head {
  display: flex;
  position: sticky;
  top: 0;
  background: var(--wb-surface-2);
  z-index: 3;
  border-bottom: 1px solid var(--wb-border);
  height: 32px;
}
.name-col {
  width: 200px;
  flex: 0 0 200px;
  padding: 7px 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--wb-text-2);
  border-right: 1px solid var(--wb-border);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.timeline-head,
.timeline {
  position: relative;
  background-image: linear-gradient(to right, var(--wb-border) 1px, transparent 1px);
  background-size: 28px 100%;
}
.day-tick {
  position: absolute;
  top: 0;
  height: 100%;
  font-size: 11px;
  color: var(--wb-text-3);
  padding-left: 4px;
  line-height: 32px;
}
.today-line {
  position: absolute;
  top: 0;
  bottom: -999px;
  width: 2px;
  background: var(--wb-danger);
  opacity: 0.55;
  z-index: 1;
  pointer-events: none;
}
.gantt-row {
  display: flex;
  border-bottom: 1px solid var(--wb-border);
  transition: background var(--wb-duration) var(--wb-ease);
}
.gantt-row:nth-child(odd) {
  background: color-mix(in srgb, var(--wb-surface-2) 55%, transparent);
}
.gantt-row:hover {
  background: color-mix(in srgb, var(--wb-brand-600) 5%, transparent);
}
.bar {
  position: absolute;
  top: 8px;
  height: 22px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--wb-brand-500), var(--wb-brand-700));
  color: #fff;
  font-size: 11px;
  line-height: 22px;
  padding: 0 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  z-index: 2;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--wb-brand-700) 28%, transparent);
  transition: transform var(--wb-duration) var(--wb-ease),
    box-shadow var(--wb-duration) var(--wb-ease);
}
.bar:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px color-mix(in srgb, var(--wb-brand-700) 35%, transparent);
}
.bar.done {
  background: linear-gradient(90deg, var(--wb-success), color-mix(in srgb, var(--wb-success) 70%, #000));
}
.edge-layer {
  position: absolute;
  pointer-events: none;
  z-index: 1;
}
</style>
