<template>
  <div class="dashboard">
    <AppHeader />

    <main class="dashboard-main">
      <div class="greeting">
        <div class="greeting-title">{{ greeting }}，{{ nickname }}</div>
        <div class="greeting-sub">
          <template v-if="dash">
            今天有 {{ openTaskCount }} 条待办 · {{ projectCount }} 个项目 ·
            本周已记录 {{ capacityMinutes }} 分钟
          </template>
          <template v-else>正在加载你的工作台…</template>
        </div>
      </div>

      <div class="layout-bar">
        <span class="hint">拖拽标题移动板块，拖右下角缩放；布局按账号保存</span>
        <span class="layout-actions">
          <el-button size="small" @click="addVisible = true">添加板块</el-button>
          <el-button size="small" :loading="saving" @click="saveLayout">保存布局</el-button>
          <el-button size="small" @click="resetLayout">恢复默认</el-button>
        </span>
      </div>

      <div v-if="loading" class="skeleton-grid">
        <div v-for="index in 8" :key="index" class="skeleton-tile" :class="{ wide: index % 5 === 1 }"></div>
      </div>
      <DashboardGrid
        v-else-if="dash"
        :layout="layout"
        :catalog="catalog"
        :data="dash.data"
        @save="saveLayoutWith"
        @remove="removeWidget"
        @open-tasks="openTasks"
        @open-project="openProject"
        @open-board="openBoard"
        @open-gantt="openGantt"
        @open-board-id="openBoardById"
        @go-report="goReport"
      />

      <el-dialog v-model="addVisible" title="添加板块" width="420px">
        <div v-for="widget in availableWidgets" :key="widget.id" class="add-row">
          <span>{{ widget.title }}</span>
          <el-button size="small" type="primary" @click="addWidget(widget)">添加</el-button>
        </div>
        <div v-if="availableWidgets.length === 0" class="muted">已全部添加</div>
      </el-dialog>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { DashboardView, LayoutItem, WidgetDef } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import DashboardGrid from "../components/dashboard/DashboardGrid.vue";
import type { ProjectBlock } from "../components/dashboard/WidgetContent.vue";
import { client } from "../stores/auth";

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const addVisible = ref(false);
const dash = ref<DashboardView | null>(null);
const layout = ref<LayoutItem[]>([]);
const catalog = ref<WidgetDef[]>([]);
const nickname = ref((JSON.parse(localStorage.getItem("workbench.user") ?? "{}") as { nickname?: string }).nickname ?? "");
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return "夜深了";
  if (hour < 12) return "早上好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});
const openTaskCount = computed(() => {
  const tasks = dash.value?.data["my-tasks"] as unknown[] | undefined;
  return tasks?.length ?? 0;
});
const projectCount = computed(() => {
  const projects = dash.value?.data["summary"] as { projects?: number } | undefined;
  return projects?.projects ?? 0;
});
const capacityMinutes = computed(() => {
  const capacity = dash.value?.data["capacity"] as { minutes?: number } | undefined;
  return capacity?.minutes ?? 0;
});

const projectBlocks = computed<ProjectBlock[]>(() =>
  Object.entries(dash.value?.data ?? {})
    .filter(([key]) => key.startsWith("project:"))
    .map(([, value]) => value as ProjectBlock)
);

const availableWidgets = computed<WidgetDef[]>(() => {
  const functionWidgets = catalog.value.filter((widget) => !layout.value.some((item) => item.id === widget.id));
  const projectWidgets = projectBlocks.value
    .filter((project) => !layout.value.some((item) => item.id === `project:${project.id}`))
    .map((project) => ({
      id: `project:${project.id}`,
      title: `项目：${project.name}`,
      group: "项目",
      defaultW: 4,
      defaultH: 2
    }));
  return [...functionWidgets, ...projectWidgets];
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await client.dashboardMe();
    dash.value = data;
    catalog.value = data.catalog;
    layout.value = data.layout;

    const savedProjectBlocks = data.layout.filter((item) => item.id.startsWith("project:"));
    if (!data.layoutSaved && savedProjectBlocks.length === 0) {
      const projectKeys = Object.keys(data.data).filter((key) => key.startsWith("project:")).slice(0, 3);
      const row = Math.max(...data.layout.map((item) => item.y + item.h), 0);
      layout.value = [
        ...data.layout,
        ...projectKeys.map((key, index) => ({ id: key, x: (index % 3) * 4, y: row, w: 4, h: 2 }))
      ];
    }
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载仪表盘失败");
  } finally {
    loading.value = false;
  }
}

async function saveLayout(): Promise<void> {
  await saveLayoutWith(layout.value);
}

async function saveLayoutWith(items: LayoutItem[]): Promise<void> {
  saving.value = true;
  try {
    await client.saveDashboardLayout(items);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存布局失败");
  } finally {
    saving.value = false;
  }
}

async function resetLayout(): Promise<void> {
  try {
    const result = await client.resetDashboardLayout();
    layout.value = result.items;
    ElMessage.success("已恢复默认布局");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "重置失败");
  }
}

async function addWidget(widget: WidgetDef): Promise<void> {
  const maxRow = Math.max(...layout.value.map((item) => item.y + item.h), 0);
  layout.value.push({ id: widget.id, x: 0, y: maxRow, w: widget.defaultW, h: widget.defaultH });
  addVisible.value = false;
  await saveLayout();
}

async function removeWidget(id: string): Promise<void> {
  layout.value = layout.value.filter((item) => item.id !== id);
  await saveLayout();
}

async function openProject(project?: ProjectBlock): Promise<void> {
  if (!project) return;
  await router.push({ name: "tasks", params: { projectId: project.id } });
}

async function openTasks(projectId: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId } });
}

async function openBoard(project?: ProjectBlock): Promise<void> {
  if (!project) return;
  await router.push({ name: "board", params: { projectId: project.id } });
}

function openBoardById(projectId: number): void {
  void router.push({ name: "board", params: { projectId } });
}

async function openGantt(project?: ProjectBlock): Promise<void> {
  if (!project) return;
  await router.push({ name: "gantt", params: { projectId: project.id } });
}

async function goReport(): Promise<void> {
  await router.push({ name: "reports" });
}

onMounted(load);
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: var(--wb-bg);
}
.dashboard-main {
  padding: 18px 20px 32px;
}
.greeting {
  margin-bottom: 14px;
}
.greeting-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--wb-text);
  letter-spacing: 0.2px;
}
.greeting-sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--wb-text-3);
}
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.skeleton-tile {
  height: 150px;
  border-radius: var(--wb-radius-lg);
  background: linear-gradient(90deg, var(--wb-surface) 25%, var(--wb-surface-2) 37%, var(--wb-surface) 63%);
  background-size: 400% 100%;
  animation: wb-shimmer 1.4s ease infinite;
  border: 1px solid var(--wb-border);
}
.skeleton-tile.wide {
  grid-column: span 2;
}
@keyframes wb-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
.layout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 0 2px;
}
.hint {
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.layout-actions {
  display: inline-flex;
  gap: 8px;
}
.add-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--wb-border);
}
.muted {
  color: var(--wb-text-3);
}
.empty {
  padding: 60px;
  text-align: center;
  color: var(--wb-text-3);
}
</style>
