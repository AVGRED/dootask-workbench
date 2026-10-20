<template>
  <el-container class="page">
    <el-header class="page-header">
      <div class="header-left">
        <span class="brand">工作台</span>
        <nav class="tabs">
          <button class="tab active" type="button">首页</button>
          <button class="tab" type="button" @click="goProjects">项目</button>
          <button class="tab" type="button" @click="goReport">日报</button>
        </nav>
      </div>
      <div class="header-right">
        <el-select v-model="status" size="small" class="status-select" @change="changeStatus">
          <el-option label="🎯 专注" value="focus" />
          <el-option label="☕ 空闲" value="idle" />
          <el-option label="🗣 会议" value="meeting" />
          <el-option label="🚶 外出" value="away" />
          <el-option label="🌴 请假" value="leave" />
        </el-select>

        <el-dropdown trigger="click" @command="onLayoutCommand">
          <el-button size="small">布局 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="add">添加板块</el-dropdown-item>
              <el-dropdown-item command="save">保存布局</el-dropdown-item>
              <el-dropdown-item command="reset" divided>恢复默认</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button size="small" class="theme-btn" @click="toggleTheme">
          {{ theme === "dark" ? "☀️" : "🌙" }}
        </el-button>

        <el-dropdown trigger="click" @command="onUserCommand">
          <span class="avatar-entry">
            <img v-if="myAvatar" class="my-avatar" :src="`/api/v1/avatars/${myAvatar}`" alt="头像" />
            <span v-else class="my-avatar placeholder">{{ (authName || "我").slice(0, 1) }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="avatar">上传头像</el-dropdown-item>
              <el-dropdown-item command="projects">全部项目</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPicked" />
      </div>
    </el-header>


    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
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
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { DashboardView, LayoutItem, WidgetDef } from "@workbench/api";
import DashboardGrid from "../components/dashboard/DashboardGrid.vue";
import type { ProjectBlock } from "../components/dashboard/WidgetContent.vue";
import { client } from "../stores/auth";
import { useTheme } from "../composables/useTheme";

const router = useRouter();
const { theme, toggleTheme } = useTheme();
const loading = ref(false);
const saving = ref(false);
const addVisible = ref(false);
const dash = ref<DashboardView | null>(null);
const layout = ref<LayoutItem[]>([]);
const catalog = ref<WidgetDef[]>([]);
const status = ref("idle");
const storedUser = JSON.parse(localStorage.getItem("workbench.user") ?? "{}") as { nickname?: string };
const authName = ref(storedUser.nickname ?? "");
const myAvatar = ref("");
const avatarInput = ref<HTMLInputElement | null>(null);

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
    status.value = data.status || "idle";
    myAvatar.value = (await client.myAvatar()).avatar;

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

async function onLayoutCommand(command: string): Promise<void> {
  if (command === "add") {
    addVisible.value = true;
  } else if (command === "save") {
    await saveLayout();
  } else if (command === "reset") {
    await resetLayout();
  }
}

async function onUserCommand(command: string): Promise<void> {
  if (command === "avatar") {
    pickAvatar();
  } else if (command === "projects") {
    await goProjects();
  } else if (command === "logout") {
    await logout();
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

async function changeStatus(): Promise<void> {
  try {
    await client.setStatus(status.value);
    ElMessage.success("状态已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "设置状态失败");
  }
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
  await router.push({ name: "report" });
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
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 20px;
  background: color-mix(in srgb, var(--wb-surface) 82%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--wb-border);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.brand {
  font-size: 16px;
  font-weight: 700;
  color: var(--wb-text);
  letter-spacing: 0.2px;
}
.brand::before {
  content: "";
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  margin-right: 8px;
  background: var(--wb-brand-600);
  transform: translateY(-1px);
}
.tabs {
  display: flex;
  gap: 2px;
  padding: 4px;
  border-radius: var(--wb-radius);
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
}
.tab {
  border: none;
  background: transparent;
  padding: 7px 14px;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--wb-text-2);
  cursor: pointer;
  transition: background var(--wb-duration) var(--wb-ease), color var(--wb-duration) var(--wb-ease);
}
.tab:hover {
  color: var(--wb-text);
  background: color-mix(in srgb, var(--wb-brand-600) 8%, transparent);
}
.tab.active {
  background: var(--wb-brand-600);
  color: #fff;
  box-shadow: var(--wb-shadow-1);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-select {
  width: 116px;
}
.theme-btn {
  padding: 5px 10px;
}
.avatar-entry {
  display: inline-flex;
  cursor: pointer;
}
.my-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--wb-border);
}
.my-avatar.placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--wb-brand-50);
  color: var(--wb-brand-700);
  font-size: 13px;
  font-weight: 600;
}
.add-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
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
