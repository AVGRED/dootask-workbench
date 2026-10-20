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
        <el-button size="small" @click="pickAvatar">我的头像</el-button>
        <img v-if="myAvatar" class="my-avatar" :src="`/api/v1/avatars/${myAvatar}`" alt="我的头像" />
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPicked" />
        <el-button size="small" @click="addVisible = true">添加板块</el-button>
        <el-button size="small" :loading="saving" @click="saveLayout">保存布局</el-button>
        <el-button size="small" @click="resetLayout">恢复默认</el-button>
        <el-button size="small" @click="goProjects">全部项目</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </el-header>

    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else ref="bentoRef" class="bento" :style="{ height: `${gridHeight}px` }">
        <div
          v-for="item in layout"
          :key="item.id"
          class="tile"
          :class="{ dragging: drag?.id === item.id }"
          :style="tileStyle(item)"
          :data-color="colorIndex[item.id] ?? 0"
          @pointerdown="onTileDown($event, item)"
        >
          <div class="tile-head">
            <span class="tile-title">{{ titleOf(item.id) }}</span>
            <span class="tile-tools">
              <span class="remove" title="移除板块" @click.stop="removeWidget(item.id)">×</span>
            </span>
          </div>
<div class="tile-body">
            <WidgetContent
              :id="item.id"
              :tasks="tasks"
              :report-submitted="reportSubmitted"
              :capacity="capacity"
              :unread="unread"
              :latest-notes="latestNotes"
              :activity="activity"
              :board-rows="boardRows"
              :gantt-tasks="ganttTasks"
              :summary="summary"
              :project="projectOf(item.id)"
              @open-tasks="openTasks"
              @open-project="openProject"
              @open-board="openBoard"
              @open-gantt="openGantt"
              @open-board-id="openBoardById"
              @go-report="goReport"
            />
          </div>
                    <div class="resize" title="拖动调整大小"></div>
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
import {
  ROW,
  ROW_HEIGHT,
  buildColorIndex,
  gridHeightOf,
  tileStyleOf
} from "../composables/bentoGrid";
import WidgetContent from "../components/dashboard/WidgetContent.vue";
import { client } from "../stores/auth";

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const addVisible = ref(false);
const dash = ref<DashboardView | null>(null);
const bentoRef = ref<HTMLElement | null>(null);
const layout = ref<LayoutItem[]>([]);
const catalog = ref<WidgetDef[]>([]);
const status = ref("idle");
const myAvatar = ref("");
const avatarInput = ref<HTMLInputElement | null>(null);


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

const colorIndex = computed(() => buildColorIndex(layout.value));
const gridHeight = computed(() => gridHeightOf(layout.value));
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
  return tileStyleOf(item, colorIndex.value);
}

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
    myAvatar.value = (await client.myAvatar()).avatar;
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
  const gridWidth = bentoRef.value?.clientWidth ?? window.innerWidth;
  const colWidth = gridWidth / 12;
  const dx = Math.round((event.clientX - drag.value.startX) / colWidth);
  const dy = Math.round((event.clientY - drag.value.startY) / ROW_HEIGHT);
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

function pickAvatar(): void {
  avatarInput.value?.click();
}

async function onAvatarPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    const result = await client.uploadAvatar(file);
    myAvatar.value = result.avatar;
    ElMessage.success("头像已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "头像上传失败");
  } finally {
    input.value = "";
  }
}

async function changeStatus(): Promise<void> {
  try {
    await client.setStatus(status.value);
    ElMessage.success("状态已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "设置状态失败");
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
  await client.logout().catch(() => undefined);
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
.my-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #dcdfe6;
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
  border: 1.5px solid #dcdfe6;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: box-shadow 0.15s, transform 0.12s;
}
.tile:hover {
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
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
  border-bottom: 1px dashed rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.45);
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
  right: 0;
  bottom: 0;
  width: 22px;
  height: 22px;
  cursor: nwse-resize;
  background:
    linear-gradient(135deg, transparent 46%, rgba(0, 0, 0, 0.18) 46%, rgba(0, 0, 0, 0.18) 52%, transparent 52%),
    linear-gradient(135deg, transparent 62%, rgba(0, 0, 0, 0.18) 62%, rgba(0, 0, 0, 0.18) 68%, transparent 68%);
  border-radius: 0 0 10px 0;
  z-index: 3;
}
.resize:hover {
  background:
    linear-gradient(135deg, transparent 46%, #409eff 46%, #409eff 52%, transparent 52%),
    linear-gradient(135deg, transparent 62%, #409eff 62%, #409eff 68%, transparent 68%);
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
