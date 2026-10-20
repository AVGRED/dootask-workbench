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
        <el-button size="small" @click="toggleTheme">{{ theme === "dark" ? "☀️ 亮色" : "🌙 暗色" }}</el-button>
        <el-button size="small" @click="goProjects">全部项目</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
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
.my-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #dcdfe6;
}
.add-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
}
.muted {
  color: #909399;
}
.empty {
  padding: 60px;
  text-align: center;
  color: #909399;
}
</style>
