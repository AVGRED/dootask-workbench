<template>
  <div v-if="id === 'my-tasks'">
    <div v-if="tasks.length === 0" class="muted">没有待办任务</div>
    <div v-for="task in tasks" :key="task.id" class="line" @click="emit('open-tasks', task.project_id)">
      <span class="dot"></span>{{ task.name }}
      <span class="muted small">{{ task.end_at ? task.end_at.slice(0, 10) : "" }}</span>
    </div>
  </div>

  <div v-else-if="id.startsWith('project:')">
    <div class="muted">{{ project?.done }}/{{ project?.total }} 完成 · {{ project?.members }} 人</div>
    <div class="progress"><div class="progress-bar" :style="{ width: `${project?.percent ?? 0}%` }"></div></div>
    <div class="project-actions">
      <el-button size="small" @click="emit('open-project', project)">任务</el-button>
      <el-button size="small" @click="emit('open-board', project)">看板</el-button>
      <el-button size="small" @click="emit('open-gantt', project)">甘特</el-button>
    </div>
  </div>

  <div v-else-if="id === 'daily-report'">
    <div class="big">{{ reportSubmitted ? "已提交" : "未提交" }}</div>
    <el-button size="small" @click="emit('go-report')">去写日报</el-button>
  </div>

  <div v-else-if="id === 'capacity'">
    <div class="big">{{ capacity.minutes }}<span class="unit"> 分钟</span></div>
    <div class="muted">近 7 天 · 可用 {{ capacity.available }} 分钟</div>
  </div>

  <div v-else-if="id === 'notifications'">
    <div class="big">{{ unread }}<span class="unit"> 未读</span></div>
    <div v-for="note in latestNotes" :key="note.id" class="line">{{ note.title }}</div>
  </div>

  <div v-else-if="id === 'activity'">
    <div v-for="log in activity" :key="log.id" class="line">
      <span class="muted small">{{ (log.created_at ?? "").slice(5, 16) }}</span> {{ log.detail }}
    </div>
  </div>

  <div v-else-if="id === 'board'">
    <div v-for="row in boardRows" :key="row.project_id" class="line" @click="emit('open-board-id', row.project_id)">
      {{ row.name }} <span class="muted small">{{ row.open_tasks }} 个未完成</span>
    </div>
  </div>

  <div v-else-if="id === 'gantt'">
    <div v-for="task in ganttTasks" :key="task.id" class="line">
      {{ task.name }}
      <span class="muted small">{{ task.start_at?.slice(0, 10) }} → {{ task.end_at?.slice(0, 10) }}</span>
    </div>
  </div>

  <div v-else-if="id === 'summary'">
    <div class="row-metrics">
      <div><div class="big">{{ summary.projects }}</div><div class="muted">项目</div></div>
      <div><div class="big">{{ summary.openTasks }}</div><div class="muted">未完成</div></div>
      <div><div class="big warn">{{ summary.overdue }}</div><div class="muted">逾期</div></div>
    </div>
  </div>

  <div v-else-if="id === 'search'">
    <el-input v-model="keyword" placeholder="搜索项目 / 任务 / 人" @keyup.enter="doSearch" />
    <div v-if="hits.length > 0" class="search-out">
      <div v-for="hit in hits" :key="hit.type + hit.id" class="line">{{ hit.title }}
        <span class="muted small">{{ hit.subtitle }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { client } from "../../stores/auth";

export interface ProjectBlock {
  id: number;
  name: string;
  members: number;
  total: number;
  done: number;
  percent: number;
}

const props = defineProps<{
  id: string;
  data: Record<string, unknown>;
}>();

const emit = defineEmits<{
  "open-tasks": [projectId: number];
  "open-project": [project: ProjectBlock | undefined];
  "open-board": [project: ProjectBlock | undefined];
  "open-gantt": [project: ProjectBlock | undefined];
  "open-board-id": [projectId: number];
  "go-report": [];
}>();

interface TaskRow {
  id: number;
  name: string;
  project_id: number;
  end_at: string | null;
}

const tasks = computed(() => (props.data["my-tasks"] as TaskRow[] | undefined) ?? []);
const project = computed(() => props.data[props.id] as ProjectBlock | undefined);
const reportSubmitted = computed(
  () => ((props.data["daily-report"] as unknown[] | undefined)?.length ?? 0) > 0
);
const capacity = computed(
  () => (props.data["capacity"] as { minutes: number; available: number } | undefined) ?? { minutes: 0, available: 0 }
);
const notifications = computed(
  () => (props.data["notifications"] as { unread?: number; latest?: { id: number; title: string }[] } | undefined) ?? {}
);
const unread = computed(() => notifications.value.unread ?? 0);
const latestNotes = computed(() => notifications.value.latest ?? []);
const activity = computed(
  () => (props.data["activity"] as { id: number; detail: string; created_at: string }[] | undefined) ?? []
);
const boardRows = computed(
  () => (props.data["board"] as { project_id: number; name: string; open_tasks: number }[] | undefined) ?? []
);
const ganttTasks = computed(
  () => (props.data["gantt"] as { id: number; name: string; start_at: string; end_at: string }[] | undefined) ?? []
);
const summary = computed(
  () =>
    (props.data["summary"] as { projects: number; openTasks: number; overdue: number } | undefined) ?? {
      projects: 0,
      openTasks: 0,
      overdue: 0
    }
);

const keyword = ref("");
const hits = ref<{ id: number; title: string; subtitle: string; type: string }[]>([]);

async function doSearch(): Promise<void> {
  const text = keyword.value.trim();
  if (!text) return;
  try {
    const result = (await client.search(text)) as { projects: unknown[]; tasks: unknown[]; users: unknown[] };
    hits.value = [
      ...result.projects.map((item) => ({ ...(item as object), type: "project" })),
      ...result.tasks.map((item) => ({ ...(item as object), type: "task" })),
      ...result.users.map((item) => ({ ...(item as object), type: "user" }))
    ] as { id: number; title: string; subtitle: string; type: string }[];
  } catch {
    hits.value = [];
  }
}
</script>

<style scoped>
.line {
  padding: 3px 0;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #67c23a;
  margin-right: 6px;
}
.muted {
  color: #909399;
}
.small {
  font-size: 12px;
  margin-left: 6px;
}
.big {
  font-size: 24px;
  font-weight: 700;
}
.big.warn {
  color: #f56c6c;
}
.unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.row-metrics {
  display: flex;
  gap: 24px;
}
.progress {
  height: 6px;
  background: #f0f2f5;
  border-radius: 3px;
  margin: 8px 0;
  overflow: hidden;
}
.progress-bar {
  height: 100%;
  background: #409eff;
}
.project-actions {
  display: flex;
  gap: 6px;
}
.search-out {
  margin-top: 6px;
}
</style>
