<template>
  <div class="detail">
    <div class="detail-head">
      <el-button link @click="goBack">← 返回项目总览</el-button>
      <div class="title-row">
        <h2>{{ project?.name || "项目详情" }}</h2>
        <span class="badge">{{ project?.members ?? 0 }} 名成员</span>
        <el-button size="small" @click="openMembers">成员管理</el-button>
        <el-button size="small" @click="openProjectEdit">编辑项目</el-button>
        <el-button size="small" type="primary" @click="openTaskDialog()">新建任务</el-button>
      </div>
      <p class="meta">
        负责人 {{ project?.owner_name || "—" }} · {{ project?.done ?? 0 }}/{{ project?.total ?? 0 }} 完成 ·
        {{ project?.overdue ?? 0 }} 逾期
      </p>
    </div>

    <el-table v-loading="loading" :data="tasks" size="small" border class="task-table">
      <el-table-column prop="name" label="任务" min-width="220" show-overflow-tooltip />
      <el-table-column label="负责人" width="120">
        <template #default="{ row }">{{ nameOf(row.ownerUserid) }}</template>
      </el-table-column>
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">{{ priorityText(row.pLevel) }}</template>
      </el-table-column>
      <el-table-column label="截止" width="120">
        <template #default="{ row }">{{ (row.endAt ?? "").slice(0, 10) || "—" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.completeAt ? 'success' : 'info'" size="small">{{ row.completeAt ? "已完成" : "进行中" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openTaskDrawer(row)">详情</el-button>
          <el-button link type="primary" size="small" @click="openTaskDialog(row)">编辑</el-button>
          <el-button v-if="!row.completeAt" link type="success" size="small" @click="complete(row)">完成</el-button>
          <el-button v-else link size="small" @click="reopen(row)">重开</el-button>
          <el-button link type="danger" size="small" @click="removeTask(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!loading && tasks.length === 0" class="empty">还没有任务，点右上角「新建任务」</div>

    <el-drawer v-model="drawerVisible" :title="currentTask?.name ?? '任务详情'" size="480px">
      <div class="drawer-section">
        <h4>附件</h4>
        <div class="attach-add">
          <input ref="attachInput" type="file" hidden @change="onAttachmentPicked" />
          <el-button size="small" :loading="attachBusy" @click="pickAttachment">上传附件</el-button>
          <span class="hint">单个文件 ≤20MB</span>
        </div>
        <div v-if="attachments.length === 0" class="muted">还没有附件</div>
        <div v-for="file in attachments" :key="file.id" class="attach-row">
          <a :href="`/api/v1/attachments/${file.id}/download`" target="_blank">{{ file.originalName }}</a>
          <span class="muted small">{{ (file.sizeBytes / 1024).toFixed(0) }} KB</span>
          <el-button link type="danger" size="small" @click="removeAttachment(file)">删除</el-button>
        </div>
      </div>

      <div class="drawer-section">
        <h4>评论</h4>
        <div class="comment-add">
          <el-input v-model="commentText" type="textarea" :rows="2" maxlength="2000" placeholder="写评论…" />
          <el-button type="primary" size="small" :loading="commentBusy" @click="postComment">发送</el-button>
        </div>
        <div v-for="comment in comments" :key="comment.id" class="comment-row">
          <div class="comment-head">
            <span class="comment-author">{{ nameOf(comment.userid) }}</span>
            <span class="muted small">{{ (comment.createdAt ?? "").slice(0, 16).replace("T", " ") }}</span>
            <el-button link type="danger" size="small" class="comment-del" @click="removeComment(comment)">删除</el-button>
          </div>
          <p class="comment-body">{{ comment.content }}</p>
        </div>
        <div v-if="comments.length === 0" class="muted">还没有评论</div>
      </div>
    </el-drawer>

    <!-- 项目信息 -->
    <el-dialog v-model="projectEditVisible" title="编辑项目" width="480px">
      <el-form :model="projectForm" label-width="80px">
        <el-form-item label="项目名称"><el-input v-model="projectForm.name" maxlength="255" /></el-form-item>
        <el-form-item label="描述">
          <el-input v-model="projectForm.desc" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProject">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员 -->
    <el-dialog v-model="membersVisible" title="成员管理" width="460px">
      <div class="member-add">
        <el-select v-model="memberToAdd" filterable placeholder="添加成员" size="small" style="flex: 1">
          <el-option
            v-for="user in candidateUsers"
            :key="user.userid"
            :label="`${user.nickname}（${user.account || user.email}）`"
            :value="user.userid"
          />
        </el-select>
        <el-button type="primary" size="small" :loading="memberBusy" @click="addMember">添加</el-button>
      </div>
      <div v-if="members.length === 0" class="empty">还没有成员</div>
      <div v-for="member in members" :key="member.userid" class="member-row">
        <span>{{ member.nickname || member.userid }}</span>
        <span class="member-role">{{ member.role || "member" }}</span>
        <el-button link type="danger" size="small" @click="removeMember(member.userid)">移除</el-button>
      </div>
    </el-dialog>

    <!-- 任务 -->
    <el-dialog v-model="taskVisible" :title="taskForm.id ? '编辑任务' : '新建任务'" width="480px">
      <el-form :model="taskForm" label-width="80px">
        <el-form-item label="任务名称"><el-input v-model="taskForm.name" maxlength="255" /></el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="taskForm.ownerUserid" filterable style="width: 100%">
            <el-option
              v-for="member in members"
              :key="member.userid"
              :label="member.nickname || String(member.userid)"
              :value="member.userid"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="taskForm.pLevel" style="width: 100%">
            <el-option label="普通" :value="0" />
            <el-option label="重要" :value="1" />
            <el-option label="紧急" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始">
          <el-date-picker v-model="taskForm.startAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止">
          <el-date-picker v-model="taskForm.endAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="taskForm.desc" type="textarea" :rows="3" maxlength="2000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { AdminProjectRow, TaskView, UserView } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = Number(route.params.id);

const loading = ref(false);
const saving = ref(false);
const project = ref<AdminProjectRow | null>(null);
const tasks = ref<TaskView[]>([]);
const members = ref<{ userid: number; nickname?: string; role?: string }[]>([]);
const users = ref<UserView[]>([]);

const projectEditVisible = ref(false);
const projectForm = reactive({ name: "", desc: "" });
const membersVisible = ref(false);
const memberToAdd = ref<number | undefined>();
const memberBusy = ref(false);
const taskVisible = ref(false);
const drawerVisible = ref(false);
const currentTask = ref<TaskView | null>(null);
const comments = ref<{ id: number; userid: number; content: string; createdAt: string }[]>([]);
const commentText = ref("");
const commentBusy = ref(false);
const attachments = ref<{ id: number; originalName: string; sizeBytes: number }[]>([]);
const attachBusy = ref(false);
const attachInput = ref<HTMLInputElement | null>(null);
const taskForm = reactive<{
  id: number;
  name: string;
  desc: string;
  ownerUserid?: number;
  pLevel: number;
  startAt: string | null;
  endAt: string | null;
}>({ id: 0, name: "", desc: "", ownerUserid: undefined, pLevel: 0, startAt: null, endAt: null });

const candidateUsers = computed(() => {
  const joined = new Set(members.value.map((member) => member.userid));
  return users.value.filter((user) => !joined.has(user.userid));
});

function nameOf(userid: number): string {
  const member = members.value.find((item) => item.userid === userid);
  return member?.nickname || String(userid);
}

function priorityText(level: number): string {
  return level === 2 ? "紧急" : level === 1 ? "重要" : "普通";
}

function goBack(): void {
  void router.push({ name: "projects" });
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const rows = await client.adminProjects();
    project.value = rows.find((row) => row.id === projectId) ?? null;
    const page = await client.listTasks(projectId, { page: 1, size: 200 });
    tasks.value = page.list;
    members.value = (await client.listMembers(projectId)) as { userid: number; nickname?: string; role?: string }[];
    if (users.value.length === 0) {
      const pageUsers = await client.listUsers({ page: 1, size: 100 });
      users.value = pageUsers.list;
    }
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

function openProjectEdit(): void {
  projectForm.name = project.value?.name ?? "";
  projectForm.desc = "";
  projectEditVisible.value = true;
}

async function saveProject(): Promise<void> {
  if (!projectForm.name.trim()) {
    ElMessage.warning("请填写项目名称");
    return;
  }
  saving.value = true;
  try {
    await client.updateProject(projectId, { name: projectForm.name.trim(), desc: projectForm.desc.trim() });
    projectEditVisible.value = false;
    ElMessage.success("项目已更新");
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

function openMembers(): void {
  membersVisible.value = true;
  memberToAdd.value = undefined;
}

async function addMember(): Promise<void> {
  if (!memberToAdd.value) return;
  memberBusy.value = true;
  try {
    await client.addMember(projectId, memberToAdd.value);
    memberToAdd.value = undefined;
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "添加失败");
  } finally {
    memberBusy.value = false;
  }
}

async function removeMember(userid: number): Promise<void> {
  try {
    await client.removeMember(projectId, userid);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "移除失败");
  }
}

function openTaskDialog(task?: TaskView): void {
  taskForm.id = task?.id ?? 0;
  taskForm.name = task?.name ?? "";
  taskForm.desc = task?.desc ?? "";
  taskForm.ownerUserid = task?.ownerUserid ?? members.value[0]?.userid;
  taskForm.pLevel = task?.pLevel ?? 0;
  taskForm.startAt = task?.startAt ?? null;
  taskForm.endAt = task?.endAt ?? null;
  taskVisible.value = true;
}

async function saveTask(): Promise<void> {
  if (!taskForm.name.trim()) {
    ElMessage.warning("请填写任务名称");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: taskForm.name.trim(),
      desc: taskForm.desc.trim(),
      ownerUserid: taskForm.ownerUserid,
      pLevel: taskForm.pLevel,
      startAt: taskForm.startAt,
      endAt: taskForm.endAt
    };
    if (taskForm.id) {
      await client.updateTask(taskForm.id, payload);
    } else {
      await client.createTask(projectId, payload);
    }
    taskVisible.value = false;
    ElMessage.success("已保存");
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function openTaskDrawer(task: TaskView): Promise<void> {
  currentTask.value = task;
  drawerVisible.value = true;
  commentText.value = "";
  await Promise.all([loadComments(task.id), loadAttachments(task.id)]);
}

async function loadComments(taskId: number): Promise<void> {
  try {
    comments.value = (await client.listComments(taskId)) as typeof comments.value;
  } catch {
    comments.value = [];
  }
}

async function postComment(): Promise<void> {
  if (!currentTask.value || !commentText.value.trim()) return;
  commentBusy.value = true;
  try {
    await client.createComment(currentTask.value.id, { content: commentText.value.trim() });
    commentText.value = "";
    await loadComments(currentTask.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "评论失败");
  } finally {
    commentBusy.value = false;
  }
}

async function removeComment(comment: { id: number }): Promise<void> {
  if (!currentTask.value) return;
  try {
    await client.deleteComment(comment.id);
    await loadComments(currentTask.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function loadAttachments(taskId: number): Promise<void> {
  try {
    attachments.value = (await client.listAttachments(taskId)) as typeof attachments.value;
  } catch {
    attachments.value = [];
  }
}

function pickAttachment(): void {
  attachInput.value?.click();
}

async function onAttachmentPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !currentTask.value) return;
  attachBusy.value = true;
  try {
    await client.uploadAttachment(currentTask.value.id, file);
    await loadAttachments(currentTask.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "上传失败");
  } finally {
    attachBusy.value = false;
    input.value = "";
  }
}

async function removeAttachment(file: { id: number }): Promise<void> {
  if (!currentTask.value) return;
  try {
    await client.deleteAttachment(file.id);
    await loadAttachments(currentTask.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function complete(task: TaskView): Promise<void> {
  try {
    await client.completeTask(task.id);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

async function reopen(task: TaskView): Promise<void> {
  try {
    await client.reopenTask(task.id);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

async function removeTask(task: TaskView): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除任务「${task.name}」？`, "删除任务", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteTask(task.id);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

onMounted(load);
</script>

<style scoped>
.detail {
  padding-bottom: 24px;
}
.detail-head {
  margin-bottom: 14px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.title-row h2 {
  margin: 0;
  font-size: 18px;
  color: var(--wb-text);
}
.badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--wb-text-3);
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
}
.meta {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--wb-text-3);
}
.task-table {
  margin-top: 6px;
}
.empty {
  padding: 24px;
  text-align: center;
  color: var(--wb-text-3);
  font-size: 13px;
}
.member-add {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--wb-border);
  font-size: 13px;
}
.member-role {
  margin-left: auto;
  font-size: 12px;
  color: var(--wb-text-3);
}
</style>

<style scoped>
.drawer-section {
  margin-bottom: 20px;
}
.drawer-section h4 {
  margin: 0 0 8px;
  font-size: 13.5px;
  color: var(--wb-text);
}
.muted {
  color: var(--wb-text-3);
  font-size: 13px;
}
.small {
  font-size: 12px;
}
.hint {
  font-size: 12px;
  color: var(--wb-text-3);
  margin-left: 8px;
}
.attach-add {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.attach-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--wb-border);
  font-size: 13px;
}
.attach-row a {
  color: var(--wb-brand-600);
  text-decoration: none;
}
.comment-add {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.comment-row {
  padding: 8px 0;
  border-bottom: 1px dashed var(--wb-border);
}
.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.comment-author {
  font-weight: 600;
  font-size: 13px;
  color: var(--wb-text);
}
.comment-del {
  margin-left: auto;
}
.comment-body {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--wb-text-2);
  white-space: pre-wrap;
}
</style>
