<template>
  <el-container class="page">
    <el-header class="page-header">
      <span class="brand">工作台</span>
      <span class="header-actions">
        <el-select v-model="status" size="small" style="width: 120px" @change="changeStatus">
          <el-option label="专注" value="focus" />
          <el-option label="空闲" value="idle" />
          <el-option label="会议" value="meeting" />
          <el-option label="外出" value="away" />
          <el-option label="请假" value="leave" />
        </el-select>
        <el-button size="small" @click="addVisible = true">添加板块</el-button>
        <el-button size="small" :loading="saving" @click="saveLayout">保存布局</el-button>
        <el-button size="small" @click="resetLayout">恢复默认</el-button>
        <el-button size="small" @click="goProjects">全部项目</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </el-header>

    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else class="bento" :style="{ height: `${gridHeight}px` }">
        <div
          v-for="item in layout"
          :key="item.id"
          class="tile"
          :class="{ dragging: drag?.id === item.id }"
          :style="tileStyle(item)"
          @pointerdown="onTileDown($event, item)"
        >
          <div class="tile-head">
            <span class="tile-title">{{ titleOf(item.id) }}</span>
            <span class="tile-tools">
              <span class="remove" title="移除板块" @click.stop="removeWidget(item.id)">×</span>
            </span>
          </div>
          <div class="tile-body">
            <div v-if="item.id === 'my-tasks'">
              <div v-if="tasks.length === 0" class="muted">没有待办任务</div>
              <div v-for="task in tasks" :key="task.id" class="line" @click="openTasks(task.project_id)">
                <span class="dot"></span>{{ task.name }}
                <span class="muted small">{{ task.end_at ? task.end_at.slice(0, 10) : "" }}</span>
              </div>
            </div>
            <div v-else-if="item.id.startsWith('project:')">
              <div class="muted">{{ projectOf(item.id)?.done }}/{{ projectOf(item.id)?.total }} 完成 · {{ projectOf(item.id)?.members }} 人</div>
              <div class="progress"><div class="progress-bar" :style="{ width: `${projectOf(item.id)?.percent ?? 0}%` }"></div></div>
              <div class="project-actions">
                <el-button size="small" @click="openProject(projectOf(item.id))">任务</el-button>
                <el-button size="small" @click="openBoard(projectOf(item.id))">看板</el-button>
                <el-button size="small" @click="openGantt(projectOf(item.id))">甘特</el-button>
              </div>
            </div>
            <div v-else-if="item.id === 'daily-report'">
              <div class="big">{{ reportSubmitted ? "已提交" : "未提交" }}</div>
              <el-button size="small" @click="goReport">去写日报</el-button>
            </div>
            <div v-else-if="item.id === 'capacity'">
              <div class="big">{{ capacity.minutes }}<span class="unit"> 分钟</span></div>
              <div class="muted">近 7 天 · 可用 {{ capacity.available }} 分钟</div>
            </div>
            <div v-else-if="item.id === 'notifications'">
              <div class="big">{{ unread }}<span class="unit"> 未读</span></div>
              <div v-for="note in latestNotes" :key="note.id" class="line">{{ note.title }}</div>
            </div>
            <div v-else-if="item.id === 'activity'">
              <div v-for="log in activity" :key="log.id" class="line">
                <span class="muted small">{{ (log.created_at ?? "").slice(5, 16) }}</span> {{ log.detail }}
              </div>
            </div>
            <div v-else-if="item.id === 'board'">
              <div v-for="row in boardRows" :key="row.project_id" class="line" @click="openBoardById(row.project_id)">
                {{ row.name }} <span class="muted small">{{ row.open_tasks }} 个未完成</span>
              </div>
            </div>
            <div v-else-if="item.id === 'gantt'">
              <div v-for="task in ganttTasks" :key="task.id" class="line">
                {{ task.name }}
                <span class="muted small">{{ task.start_at?.slice(0, 10) }} → {{ task.end_at?.slice(0, 10) }}</span>
              </div>
            </div>
            <div v-else-if="item.id === 'summary'">
              <div class="row-metrics">
                <div><div class="big">{{ summary.projects }}</div><div class="muted">项目</div></div>
                <div><div class="big">{{ summary.openTasks }}</div><div class="muted">未完成</div></div>
                <div><div class="big warn">{{ summary.overdue }}</div><div class="muted">逾期</div></div>
              </div>
            </div>
            <div v-else-if="item.id === 'search'">
              <el-input v-model="searchKeyword" placeholder="搜索项目 / 任务 / 人" @keyup.enter="doSearch" />
              <div v-if="searchResult" class="search-out">
                <div v-for="hit in flatHits" :key="hit.type + hit.id" class="line">{{ hit.title }}
                  <span class="muted small">{{ hit.subtitle }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="resize"></div>
        </div>
      </div>

      <el-dialog v-model="addVisible" title="添加板块" width="420px">
        <div v-for="widget in availableWidgets" :key="widget.id" class="add-row">
          <span>{{ widget.title }}</span>
          <el-button size="small" type="primary" @click="addWidget(widget)">添加</el-button>
        </div>
        <div v-if="availableWidgets.length === 0" class="muted">已全部添加</div>
      </el-dialog>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { DashboardView, LayoutItem, WidgetDef } from "@workbench/api";
import { client } from "../stores/auth";

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const addVisible = ref(false);
const dash = ref<DashboardView | null>(null);
const layout = ref<LayoutItem[]>([]);
const catalog = ref<WidgetDef[]>([]);
const status = ref("idle");
const searchKeyword = ref("");
const searchResult = ref<{ projects: unknown[]; tasks: unknown[]; users: unknown[] } | null>(null);

const ROW = 112;
const COL_PCT = 100 / 12;

const drag = ref<{ id: string; startX: number; startY: number; startCol: number; startRow: number; w: number; h: number; mode: "move" | "resize" } | null>(null);

const tasks = computed(() => (dash.value?.data["my-tasks"] as { id: number; name: string; project_id: number; end_at: string | null }[]) ?? []);
const reportSubmitted = computed(() => {
  const rows = (dash.value?.data["daily-report"] as unknown[]) ?? [];
  return rows.length > 0;
});
const capacity = computed(() => (dash.value?.data["capacity"] as { minutes: number; available: number }) ?? { minutes: 0, available: 0 });
const unread = computed(() => ((dash.value?.data["notifications"] as { unread: number })?.unread) ?? 0);
const latestNotes = computed(() => ((dash.value?.data["notifications"] as { latest?: { id: number; title: string }[] })?.latest) ?? []);
const activity = computed(() => (dash.value?.data["activity"] as { id: number; detail: string; created_at: string }[]) ?? []);
const boardRows = computed(() => (dash.value?.data["board"] as { project_id: number; name: string; open_tasks: number }[]) ?? []);
const ganttTasks = computed(() => (dash.value?.data["gantt"] as { id: number; name: string; start_at: string; end_at: string }[]) ?? []);
const summary = computed(() => (dash.value?.data["summary"] as { projects: number; openTasks: number; overdue: number }) ?? { projects: 0, openTasks: 0, overdue: 0 });
const flatHits = computed(() => {
  if (!searchResult.value) return [] as { id: number; title: string; subtitle: string; type: string }[];
  return [
    ...searchResult.value.projects.map((item) => ({ ...(item as object), type: "project" })),
    ...searchResult.value.tasks.map((item) => ({ ...(item as object), type: "task" })),
    ...searchResult.value.users.map((item) => ({ ...(item as object), type: "user" }))
  ] as { id: number; title: string; subtitle: string; type: string }[];
});

const gridHeight = computed(() => Math.max(...layout.value.map((item) => item.y + item.h), 4) * ROW + 8);
const availableWidgets = computed(() => {
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
  return [...functionWidgets, ...projectWidgets] as WidgetDef[];
});

interface ProjectBlock {
  id: number;
  name: string;
  members: number;
  total: number;
  done: number;
  percent: number;
}

const projectBlocks = computed<ProjectBlock[]>(() =>
  Object.entries(dash.value?.data ?? {})
    .filter(([key]) => key.startsWith("project:"))
    .map(([, value]) => value as ProjectBlock)
);

function projectOf(id: string): ProjectBlock | undefined {
  return dash.value?.data[id] as ProjectBlock | undefined;
}

function titleOf(id: string): string {
  if (id.startsWith("project:")) {
    return projectOf(id)?.name ?? "项目";
  }
  return catalog.value.find((widget) => widget.id === id)?.title ?? id;
}

function tileStyle(item: LayoutItem): Record<string, string> {
  return {
    left: `calc(${item.x * COL_PCT}% + 4px)`,
    width: `calc(${item.w * COL_PCT}% - 8px)`,
    top: `${item.y * ROW + 4}px`,
    height: `${item.h * ROW - 8}px`
  };
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await client.dashboardMe();
    dash.value = data;
    catalog.value = data.catalog;
    layout.value = data.layout;
    const savedProjectBlocks = data.layout.filter((item) => item.id.startsWith("project:"));
    if (savedProjectBlocks.length === 0) {
      const projectKeys = Object.keys(data.data).filter((key) => key.startsWith("project:")).slice(0, 3);
      let row = Math.max(...data.layout.map((item) => item.y + item.h), 0);
      layout.value = [
        ...data.layout,
        ...projectKeys.map((key, index) => ({
          id: key,
          x: (index % 3) * 4,
          y: row,
          w: 4,
          h: 2
        }))
      ];
    }
    status.value = data.status || "idle";
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载仪表盘失败");
  } finally {
    loading.value = false;
  }
}

function onTileDown(event: PointerEvent, item: LayoutItem): void {
  const target = event.target as HTMLElement;
  if (target.classList.contains("resize")) {
    startResize(event, item);
  } else if (target.closest(".tile-head")) {
    startDrag(event, item);
  }
}

function startDrag(event: PointerEvent, item: LayoutItem): void {
  drag.value = {
    id: item.id,
    startX: event.clientX,
    startY: event.clientY,
    startCol: item.x,
    startRow: item.y,
    w: item.w,
    h: item.h,
    mode: "move"
  };
  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onUp, { once: true });
}

function startResize(event: PointerEvent, item: LayoutItem): void {
  drag.value = {
    id: item.id,
    startX: event.clientX,
    startY: event.clientY,
    startCol: item.x,
    startRow: item.y,
    w: item.w,
    h: item.h,
    mode: "resize"
  };
  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onUp, { once: true });
}

function onMove(event: PointerEvent): void {
  if (!drag.value) return;
  const item = layout.value.find((entry) => entry.id === drag.value!.id);
  if (!item) return;
  const colWidth = (event.currentTarget as Window).innerWidth / 12 - 2;
  const dx = Math.round((event.clientX - drag.value.startX) / colWidth);
  const dy = Math.round((event.clientY - drag.value.startY) / ROW);
  if (drag.value.mode === "move") {
    item.x = Math.min(Math.max(drag.value.startCol + dx, 0), 12 - item.w);
    item.y = Math.max(drag.value.startRow + dy, 0);
  } else {
    item.w = Math.min(Math.max(drag.value.w + dx, 2), 12 - item.x);
    item.h = Math.min(Math.max(drag.value.h + dy, 1), 6);
  }
}

async function onUp(): Promise<void> {
  window.removeEventListener("pointermove", onMove);
  drag.value = null;
  await saveLayout();
}

async function saveLayout(): Promise<void> {
  saving.value = true;
  try {
    await client.saveDashboardLayout(layout.value);
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
  layout.value.push({ id: widget.id, x: 0, y: gridHeight.value / ROW, w: widget.defaultW, h: widget.defaultH });
  addVisible.value = false;
  await saveLayout();
}

async function removeWidget(id: string): Promise<void> {
  layout.value = layout.value.filter((item) => item.id !== id);
  await saveLayout();
}

async function changeStatus(): Promise<void> {
  try {
    await client.setStatus(status.value);
    ElMessage.success("状态已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "设置状态失败");
  }
}

async function doSearch(): Promise<void> {
  if (!searchKeyword.value.trim()) return;
  try {
    searchResult.value = (await client.search(searchKeyword.value.trim())) as never;
  } catch {
    searchResult.value = null;
  }
}

async function openProject(project?: ProjectBlock): Promise<void> {
  if (!project) return;
  await router.push({ name: "tasks", params: { projectId: project.id } });
}
async function openTasks(projectId: number): Promise<void> {
  await router.push({ name: "tasks", params: { projectId } });
}
async function openGantt(project?: ProjectBlock): Promise<void> {
  if (!project) return;
  await router.push({ name: "gantt", params: { projectId: project.id } });
}
function openBoardById(projectId: number): void {
  void router.push({ name: "board", params: { projectId } });
}

async function openBoard(project?: ProjectBlock): Promise<void> {
  if (!project) return;
  await router.push({ name: "board", params: { projectId: project.id } });
}
async function goReport(): Promise<void> {
  await router.push({ name: "reports" });
}
async function goProjects(): Promise<void> {
  await router.push({ name: "projects" });
}
async function logout(): Promise<void> {
  localStorage.removeItem("workbench.token");
  localStorage.removeItem("workbench.user");
  await router.replace({ name: "login" });
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
.brand {
  font-size: 18px;
  font-weight: 700;
}
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.bento {
  position: relative;
  background-image: linear-gradient(#f5f7fa 1px, transparent 1px), linear-gradient(90deg, #f5f7fa 1px, transparent 1px);
  background-size: 100% 112px, calc(100% / 12) 112px;
  border-radius: 8px;
}
.tile {
  position: absolute;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: box-shadow 0.15s;
}
.tile.dragging {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.35);
  z-index: 5;
}
.tile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f2f5;
  cursor: grab;
  user-select: none;
}
.tile-title {
  font-weight: 600;
  font-size: 14px;
}
.tile-tools .remove {
  color: #c0c4cc;
  cursor: pointer;
  padding: 0 4px;
}
.tile-body {
  flex: 1;
  padding: 10px 12px;
  overflow: auto;
  font-size: 13px;
}
.resize {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 14px;
  height: 14px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, #dcdfe6 50%);
  border-radius: 0 0 8px 0;
}
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
.dot.blue {
  background: #409eff;
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
.add-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
}
.project-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
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
.empty {
  padding: 60px;
  text-align: center;
  color: #909399;
}
</style>
