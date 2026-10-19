<template>
  <div>
    <div class="hero">
      <div class="hero-hello">你好，{{ auth.user?.nickname || "同事" }}</div>
      <div class="hero-date">{{ todayText }}</div>
      <div class="status-chips">
        <button
          v-for="item in STATUS"
          :key="item.value"
          class="chip"
          :class="{ active: status === item.value }"
          type="button"
          @click="changeStatus(item.value)"
        >
          {{ item.emoji }} {{ item.label }}
        </button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat"><div class="stat-num">{{ summary.projects }}</div><div class="stat-label">项目</div></div>
      <div class="stat"><div class="stat-num">{{ summary.openTasks }}</div><div class="stat-label">未完成</div></div>
      <div class="stat"><div class="stat-num warn">{{ summary.overdue }}</div><div class="stat-label">逾期</div></div>
    </div>

    <div class="quick-grid">
      <div class="quick" @click="goReport">
        <div class="quick-title">今日日报</div>
        <div class="quick-value" :class="{ ok: reportDone }">{{ reportDone ? "已提交" : "未提交" }}</div>
        <div class="quick-hint">{{ reportDone ? "辛苦了" : "点这里去写" }}</div>
      </div>
      <div class="quick" @click="goProfile">
        <div class="quick-title">未读通知</div>
        <div class="quick-value">{{ unread }}</div>
        <div class="quick-hint">点击查看</div>
      </div>
    </div>

    <div class="section-head">
      <span>我的任务</span>
      <span class="muted">{{ tasks.length }} 条</span>
    </div>
    <div v-if="tasks.length === 0" class="empty">今天没有待办，轻松一下</div>
    <div v-for="task in tasks" :key="task.id" class="card task" @click="openTask(task.project_id)">
      <div class="row between">
        <span class="task-name">{{ task.name }}</span>
        <button class="ghost small" type="button" @click.stop="complete(task.id)">完成</button>
      </div>
      <div class="card-meta">{{ task.end_at ? "截止 " + task.end_at.slice(0, 10) : "无截止日期" }}</div>
    </div>

    <div class="section-head">
      <span>我的项目</span>
      <router-link to="/projects" class="link">全部</router-link>
    </div>
    <div class="hscroll">
      <div v-for="project in projects" :key="project.id" class="card project" @click="openProject(project.id)">
        <div class="project-name">{{ project.name }}</div>
        <div class="card-meta">{{ project.members }} 人</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { DashboardView } from "@workbench/api";
import { client, useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const dash = ref<DashboardView | null>(null);
const status = ref("idle");
const error = ref("");

const STATUS = [
  { value: "focus", label: "专注", emoji: "🎯" },
  { value: "meeting", label: "会议", emoji: "🗣" },
  { value: "away", label: "外出", emoji: "🚶" },
  { value: "leave", label: "请假", emoji: "🌴" },
  { value: "idle", label: "空闲", emoji: "☕" }
];

const todayText = computed(() => {
  const now = new Date();
  const week = ["日", "一", "二", "三", "四", "五", "六"][now.getDay()];
  return `${now.getMonth() + 1} 月 ${now.getDate()} 日 · 周${week}`;
});

const tasks = computed(
  () => (dash.value?.data["my-tasks"] as { id: number; name: string; project_id: number; end_at: string | null }[]) ?? []
);
const projects = computed(() => {
  const data = dash.value?.data ?? {};
  return Object.keys(data)
    .filter((key) => key.startsWith("project:"))
    .map((key) => data[key] as { id: number; name: string; members: number })
    .slice(0, 6);
});
const reportDone = computed(() => {
  const rows = dash.value?.data["daily-report"] as unknown[] | undefined;
  return (rows?.length ?? 0) > 0;
});

const unread = computed(() => {
  const box = dash.value?.data["notifications"] as { unread?: number } | undefined;
  return box?.unread ?? 0;
});

const summary = computed(
  () => (dash.value?.data["summary"] as { projects: number; openTasks: number; overdue: number }) ?? { projects: 0, openTasks: 0, overdue: 0 }
);

async function load(): Promise<void> {
  try {
    const data = await client.dashboardMe();
    dash.value = data;
    status.value = data.status || "idle";
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  }
}

async function changeStatus(value: string): Promise<void> {
  try {
    await client.setStatus(value);
    status.value = value;
  } catch {
    // 状态失败不影响其他内容
  }
}

async function complete(taskId: number): Promise<void> {
  try {
    await client.completeTask(taskId);
    await load();
  } catch {
    // 忽略单条失败
  }
}

async function openTask(projectId: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId } });
}

async function goReport(): Promise<void> {
  await router.push({ name: "report" });
}

async function goProfile(): Promise<void> {
  await router.push({ name: "profile" });
}

async function openProject(projectId: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId } });
}

onMounted(load);
</script>

<style scoped>
.hero {
  background: linear-gradient(150deg, #409eff, #79bbff 55%, #a8c6ee);
  border-radius: 16px;
  padding: 16px;
  color: #fff;
  margin-bottom: 12px;
}
.hero-hello {
  font-size: 20px;
  font-weight: 700;
}
.hero-date {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.85;
}
.status-chips {
  display: flex;
  gap: 6px;
  margin-top: 12px;
  overflow-x: auto;
}
.chip {
  flex: 0 0 auto;
  padding: 6px 12px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 13px;
}
.chip.active {
  background: #fff;
  color: #409eff;
  font-weight: 600;
}
.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 4px;
}
.quick {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
}
.quick-title {
  font-size: 12px;
  color: var(--muted);
}
.quick-value {
  font-size: 20px;
  font-weight: 700;
  margin-top: 4px;
}
.quick-value.ok {
  color: #67c23a;
}
.quick-hint {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.stat {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  text-align: center;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
}
.stat-num.warn {
  color: #f56c6c;
}
.stat-label {
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 4px 8px;
  font-weight: 600;
}
.muted {
  color: var(--muted);
  font-weight: 400;
  font-size: 12px;
}
.task-name {
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.between {
  justify-content: space-between;
}
.small {
  font-size: 12px;
  padding: 4px 10px;
}
.hscroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.project {
  flex: 0 0 160px;
  margin-bottom: 0;
}
.project-name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.link {
  font-size: 13px;
  color: var(--brand);
  text-decoration: none;
  font-weight: 400;
}
</style>
