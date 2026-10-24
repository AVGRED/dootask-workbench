<template>
  <div v-if="id === 'my-tasks'">
    <div class="todo-add">
      <input
        v-model="newTodo"
        class="todo-input"
        placeholder="加一条个人待办，回车保存"
        maxlength="200"
        @keyup.enter="addTodo"
      />
    </div>
    <div v-for="todo in todos" :key="'todo-' + todo.id" class="line todo-line">
      <input class="todo-check" type="checkbox" :checked="todo.done === 1" @change="toggleTodo(todo)" />
      <span class="todo-title" :class="{ done: todo.done === 1 }">{{ todo.title }}</span>
      <button class="todo-del" type="button" title="删除" @click="removeTodo(todo)">×</button>
    </div>
    <div v-if="todos.length === 0" class="muted">今天还没有个人待办</div>

    <div class="todo-sep">项目任务</div>
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
    <div class="big">{{ isAdmin ? "免提交" : reportSubmitted ? "已提交" : "未提交" }}</div>
    <span v-if="isAdmin" class="muted">管理员无需提交日报</span>
    <el-button v-else size="small" @click="emit('go-report')">去写日报</el-button>
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
import { computed, onMounted, ref } from "vue";
import { useAuthStore } from "../../stores/auth";
import type { PersonalTodo } from "@workbench/api";
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
const auth = useAuthStore();
const isAdmin = computed(() => {
  try {
    return auth.user?.role === "admin";
  } catch {
    return false;
  }
});

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

const todos = ref<PersonalTodo[]>([]);
const newTodo = ref("");

async function loadTodos(): Promise<void> {
  try {
    todos.value = await client.myTodos();
  } catch {
    todos.value = [];
  }
}

async function addTodo(): Promise<void> {
  const title = newTodo.value.trim();
  if (!title) return;
  newTodo.value = "";
  await client.createTodo(title);
  await loadTodos();
}

async function toggleTodo(todo: PersonalTodo): Promise<void> {
  await client.updateTodo(todo.id, { done: todo.done !== 1 });
  await loadTodos();
}

async function removeTodo(todo: PersonalTodo): Promise<void> {
  await client.deleteTodo(todo.id);
  await loadTodos();
}

onMounted(() => {
  if (props.id === "my-tasks") void loadTodos();
});

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
  background: var(--wb-success);
  margin-right: 6px;
}
.muted {
  color: var(--wb-text-3);
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
  color: var(--wb-danger);
}
.unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--wb-text-3);
}
.row-metrics {
  display: flex;
  gap: 24px;
}
.progress {
  height: 6px;
  background: var(--wb-surface-2);
  border-radius: 3px;
  margin: 8px 0;
  overflow: hidden;
}
.progress-bar {
  height: 100%;
  background: var(--wb-brand-500);
}
.project-actions {
  display: flex;
  gap: 6px;
}
.search-out {
  margin-top: 6px;
}
.todo-add {
  margin-bottom: 8px;
}
.todo-input {
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  color: var(--wb-text);
  background: var(--wb-surface-2);
  border: 1px dashed var(--wb-border-strong);
  border-radius: 10px;
  outline: none;
}
.todo-input:focus {
  border-style: solid;
  border-color: var(--wb-brand-400);
  background: var(--wb-surface);
}
.todo-line {
  align-items: center;
  gap: 8px;
  cursor: default;
}
.todo-check {
  width: 14px;
  height: 14px;
  accent-color: var(--wb-brand-600);
  cursor: pointer;
  flex: 0 0 auto;
}
.todo-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-title.done {
  color: var(--wb-text-3);
  text-decoration: line-through;
}
.todo-del {
  border: none;
  background: none;
  color: var(--wb-text-3);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  opacity: 0;
  padding: 0 2px;
}
.todo-line:hover .todo-del {
  opacity: 1;
}
.todo-sep {
  margin: 12px 0 6px;
  font-size: 11.5px;
  color: var(--wb-text-3);
  letter-spacing: 0.3px;
}
</style>
