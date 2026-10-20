<template>
  <el-container class="page">
    <AppHeader />

    <el-main>
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新建项目</el-button>
        <el-input v-model="keyword" placeholder="搜索项目名称" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
        <el-input v-model="searchKeyword" placeholder="全局搜索项目/任务/人" clearable style="width: 220px" @keyup.enter="runSearch" />
        <el-button @click="runSearch">搜索</el-button>
      </div>

      <div v-loading="loading" class="project-grid">
        <div v-for="row in rows" :key="row.id" class="project-card">
          <div class="card-top">
            <span class="card-name">{{ row.name }}</span>
            <el-tag size="small" :type="row.archived ? 'info' : 'success'">
              {{ row.archived ? "已归档" : "进行中" }}
            </el-tag>
          </div>
          <div class="card-desc">{{ row.desc || "暂无描述" }}</div>
          <div class="card-meta">#{{ row.id }} · {{ row.memberCount }} 名成员</div>
          <div class="card-actions">
            <el-button size="small" type="primary" @click="openTasks(row as ProjectView)">任务</el-button>
            <el-button size="small" @click="openBoard(row as ProjectView)">看板</el-button>
            <el-button size="small" @click="openGantt(row as ProjectView)">甘特</el-button>
            <el-dropdown trigger="click">
              <el-button size="small" text>更多 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openPlanning(row as ProjectView)">规划</el-dropdown-item>
                  <el-dropdown-item @click="openProjectReport(row as ProjectView)">报表</el-dropdown-item>
            <el-dropdown-item @click="openActivities(row as ProjectView)">动态</el-dropdown-item>
                  <el-dropdown-item @click="openMembers(row as ProjectView)">成员</el-dropdown-item>
                  <el-dropdown-item v-if="!row.archived" @click="archive(row as ProjectView)">归档</el-dropdown-item>
                  <el-dropdown-item divided @click="remove(row as ProjectView)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
        <div v-if="rows.length === 0 && !loading" class="empty">还没有项目，点右上角新建</div>
      </div>


      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </el-main>

    <el-dialog v-model="createVisible" title="新建项目" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.desc" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="membersVisible" :title="`成员 · ${current?.name ?? ''}`" size="420px">
      <div class="members">
        <div class="members-add">
          <el-input v-model="newUserid" placeholder="用户 ID" style="width: 160px" />
          <el-button type="primary" @click="addMember">添加成员</el-button>
        </div>
        <el-table :data="members" size="small" border>
          <el-table-column prop="userid" label="用户 ID" />
          <el-table-column label="角色">
            <template #default="{ row }">
              <el-tag :type="row.owner ? 'success' : 'info'">{{ row.owner ? "负责人" : "成员" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="!row.owner" link type="danger" @click="dropMember(row.userid)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-drawer v-model="searchVisible" title="搜索结果" size="460px">
      <div v-if="searchResult" class="search-group">
        <div class="search-title">项目（{{ searchResult.projects.length }}）</div>
        <div v-for="item in searchResult.projects" :key="'p' + item.id" class="search-item" @click="openProject(item)">
          {{ item.title }}
        </div>
        <div class="search-title">任务（{{ searchResult.tasks.length }}）</div>
        <div v-for="item in searchResult.tasks" :key="'t' + item.id" class="search-item" @click="openTask(item)">
          {{ item.title }} <span class="search-sub">{{ item.subtitle }}</span>
        </div>
        <div class="search-title">成员（{{ searchResult.users.length }}）</div>
        <div v-for="item in searchResult.users" :key="'u' + item.id" class="search-item">
          {{ item.title }} <span class="search-sub">{{ item.subtitle }}</span>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="activityVisible" :title="`动态 · ${activityProject?.name ?? ''}`" size="480px">
      <el-table :data="activities" size="small" border>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column prop="userid" label="用户" width="70" />
        <el-table-column prop="actionText" label="动作" width="100" />
        <el-table-column prop="detail" label="详情" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="activityTotal"
        :page-size="10"
        :current-page="activityPage"
        @current-change="onActivityPage"
      />
    </el-drawer>

    <el-drawer v-model="notifyVisible" title="消息" size="420px">
      <div class="notify-actions">
        <el-button size="small" @click="readAll">全部已读</el-button>
        <span class="notify-unread">未读 {{ unread }}</span>
      </div>
      <el-empty v-if="notifications.length === 0" description="没有新消息" />
      <div v-for="item in notifications" :key="item.id" class="notify-item">
        <div class="notify-title">
          {{ item.title }}
          <el-tag v-if="!item.read" size="small" type="warning">未读</el-tag>
        </div>
        <div class="notify-meta">{{ item.createdAt }}</div>
        <el-button v-if="!item.read" link type="primary" @click="readOne(item as NotificationView)">标为已读</el-button>
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { ActivityView, MemberView, NotificationView, ProjectView, SearchItem, SearchResult } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const router = useRouter();

const rows = ref<ProjectView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const loading = ref(false);
const saving = ref(false);
const createVisible = ref(false);
const form = reactive({ name: "", desc: "" });

const membersVisible = ref(false);
const current = ref<ProjectView | null>(null);
const members = ref<MemberView[]>([]);
const newUserid = ref("");
const searchVisible = ref(false);
const searchKeyword = ref("");
const searchResult = ref<SearchResult | null>(null);
const activityVisible = ref(false);
const activityProject = ref<ProjectView | null>(null);
const activities = ref<ActivityView[]>([]);
const activityTotal = ref(0);
const activityPage = ref(1);
const notifyVisible = ref(false);
const notifications = ref<NotificationView[]>([]);
const unread = ref(0);

async function refreshUnread(): Promise<void> {
  try {
    unread.value = await client.unreadCount();
  } catch {
    unread.value = 0;
  }
}

async function runSearch(): Promise<void> {
  if (!searchKeyword.value.trim()) return;
  try {
    searchResult.value = await client.search(searchKeyword.value.trim());
    searchVisible.value = true;
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "搜索失败");
  }
}

async function openProject(item: SearchItem): Promise<void> {
  searchVisible.value = false;
  await router.push({ name: "tasks", params: { projectId: item.id } });
}

async function openTask(item: SearchItem): Promise<void> {
  const match = /#(\d+)/.exec(item.subtitle);
  if (!match) return;
  searchVisible.value = false;
  await router.push({ name: "tasks", params: { projectId: Number(match[1]) } });
}

async function openProjectReport(row: ProjectView): Promise<void> {
  await router.push({ name: "report", params: { projectId: row.id } });
}

async function openActivities(row: ProjectView): Promise<void> {
  activityProject.value = row;
  activityPage.value = 1;
  activityVisible.value = true;
  await loadActivities();
}

async function loadActivities(): Promise<void> {
  if (!activityProject.value) return;
  try {
    const result = await client.listActivities(activityProject.value.id, {
      page: activityPage.value,
      size: 10
    });
    activities.value = result.list;
    activityTotal.value = result.total;
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载动态失败");
  }
}

function onActivityPage(next: number): void {
  activityPage.value = next;
  void loadActivities();
}

async function loadNotifications(): Promise<void> {
  try {
    notifications.value = await client.listNotifications();
    unread.value = await client.unreadCount();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载消息失败");
  }
}

async function readOne(item: NotificationView): Promise<void> {
  try {
    await client.markNotificationRead(item.id);
    await loadNotifications();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

async function readAll(): Promise<void> {
  try {
    await client.markAllNotificationsRead();
    await loadNotifications();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

async function reload(): Promise<void> {
  loading.value = true;
  try {
    const result = await client.listProjects({ page: page.value, size: size.value, keyword: keyword.value });
    rows.value = result.list;
    total.value = result.total;
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

function onPageChange(next: number): void {
  page.value = next;
  void reload();
}

function openCreate(): void {
  form.name = "";
  form.desc = "";
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  try {
    await client.createProject({ name: form.name, desc: form.desc });
    ElMessage.success("已创建");
    createVisible.value = false;
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
  }
}

async function archive(row: ProjectView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定归档项目「${row.name}」？`, "归档确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.archiveProject(row.id);
    ElMessage.success("已归档");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "归档失败");
  }
}

async function remove(row: ProjectView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」？该操作不可恢复。`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteProject(row.id);
    ElMessage.success("已删除");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function openMembers(row: ProjectView): Promise<void> {
  current.value = row;
  membersVisible.value = true;
  await loadMembers(row.id);
}

async function openTasks(row: ProjectView): Promise<void> {
  await router.push({ name: "tasks", params: { projectId: row.id } });
}

async function openBoard(row: ProjectView): Promise<void> {
  await router.push({ name: "board", params: { projectId: row.id } });
}

async function openGantt(row: ProjectView): Promise<void> {
  await router.push({ name: "gantt", params: { projectId: row.id } });
}

async function openPlanning(row: ProjectView): Promise<void> {
  await router.push({ name: "planning", params: { projectId: row.id } });
}

async function loadMembers(projectId: number): Promise<void> {
  try {
    members.value = await client.listMembers(projectId);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载成员失败");
  }
}

async function addMember(): Promise<void> {
  const userid = Number(newUserid.value);
  if (!current.value || !Number.isFinite(userid) || userid <= 0) {
    ElMessage.warning("请输入有效的用户 ID");
    return;
  }
  try {
    await client.addMember(current.value.id, userid);
    newUserid.value = "";
    await loadMembers(current.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "添加失败");
  }
}

async function dropMember(userid: number): Promise<void> {
  if (!current.value) return;
  try {
    await client.removeMember(current.value.id, userid);
    await loadMembers(current.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "移除失败");
  }
}

onMounted(() => {
  void reload();
  void refreshUnread();
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.project-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.15s, transform 0.15s;
}
.project-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.card-name {
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-desc {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
  min-height: 36px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
.card-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.empty {
  grid-column: 1 / -1;
  padding: 40px;
  text-align: center;
  color: #909399;
}
.pager {
  margin-top: 12px;
}
.members-add {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.notify-badge {
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
}
.notify-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.notify-unread {
  color: #909399;
  font-size: 12px;
}
.notify-item {
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
}
.notify-title {
  display: flex;
  gap: 6px;
  align-items: center;
}
.notify-meta {
  color: #909399;
  font-size: 12px;
  margin: 4px 0;
}
.search-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.search-title {
  margin: 12px 0 4px;
  font-weight: 600;
  font-size: 13px;
  color: #606266;
}
.search-item {
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
}
.search-item:hover {
  background: #f5f7fa;
}
.search-sub {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}
</style>
