<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>
        <el-button link type="primary" @click="goBack">← 返回项目</el-button>
        甘特图 · 项目 {{ projectId }}
      </span>
      <span v-if="gantt" class="range">{{ gantt.rangeStart }} ~ {{ gantt.rangeEnd }}</span>
    </el-header>
    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!gantt || gantt.tasks.length === 0" class="empty">还没有带开始/结束日期的任务</div>
      <div v-else class="gantt" :style="{ width: `${totalWidth}px` }">
        <div class="gantt-head">
          <div class="name-col">任务</div>
          <div class="timeline-head" :style="{ width: `${timelineWidth}px` }">
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
            stroke="#909399"
            stroke-width="1.5"
            marker-end="url(#arrow)"
          />
        </svg>
        <svg width="0" height="0">
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#c0c4cc" />
            </marker>
          </defs>
        </svg>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { GanttTask, GanttView } from "@workbench/api";
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
.range {
  color: #909399;
  font-size: 13px;
}
.gantt {
  position: relative;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: auto;
}
.gantt-head {
  display: flex;
  position: sticky;
  top: 0;
  background: #fafafa;
  z-index: 2;
  border-bottom: 1px solid #ebeef5;
  height: 30px;
}
.name-col {
  width: 200px;
  flex: 0 0 200px;
  padding: 6px 10px;
  font-size: 13px;
  border-right: 1px solid #ebeef5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.timeline-head,
.timeline {
  position: relative;
  background-image: linear-gradient(to right, #f0f2f5 1px, transparent 1px);
  background-size: 28px 100%;
}
.day-tick {
  position: absolute;
  top: 0;
  height: 100%;
  font-size: 11px;
  color: #909399;
  padding-left: 3px;
}
.gantt-row {
  display: flex;
  border-bottom: 1px solid #f5f7fa;
}
.bar {
  position: absolute;
  top: 6px;
  height: 20px;
  border-radius: 4px;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  line-height: 20px;
  padding: 0 6px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  z-index: 2;
}
.bar.done {
  background: #67c23a;
}
.edge-layer {
  position: absolute;
  pointer-events: none;
  z-index: 1;
}
</style>
