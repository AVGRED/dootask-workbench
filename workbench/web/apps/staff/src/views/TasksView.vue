<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>
        <el-button link type="primary" @click="goBack">← 返回项目</el-button>
        任务 · 项目 {{ projectId }}
      </span>
      <el-button type="primary" @click="openCreate">新建任务</el-button>
    </el-header>
    <el-main>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索任务标题" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="标题" min-width="220" />
        <el-table-column prop="desc" label="描述" min-width="220" />
        <el-table-column label="负责人" width="110">
          <template #default="{ row }">{{ row.ownerUserid || "-" }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.pLevel > 0 ? 'warning' : 'info'">{{ row.pLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="里程碑" width="110">
          <template #default="{ row }">{{ row.milestoneId || "-" }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as TaskView)">编辑</el-button>
            <el-button link type="primary" @click="openWorklogs(row as TaskView)">工时</el-button>
            <el-button link type="primary" @click="openComments(row as TaskView)">评论</el-button>
            <el-button link type="primary" @click="openFiles(row as TaskView)">附件</el-button>
            <el-button link type="primary" @click="complete(row as TaskView)">完成</el-button>
            <el-button link type="danger" @click="remove(row as TaskView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </el-main>

    <el-dialog v-model="createVisible" title="新建任务" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.desc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input v-model="ownerInput" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.pLevel" :min="0" :max="9" />
        </el-form-item>
        <el-form-item label="里程碑">
          <el-select v-model="form.milestoneId" clearable placeholder="可留空" style="width: 100%">
            <el-option v-for="item in milestones" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="worklogVisible" title="工时" width="560px">
      <el-form :inline="true" :model="worklogForm">
        <el-form-item label="分钟">
          <el-input-number v-model="worklogForm.minutes" :min="1" :max="1440" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="worklogForm.workDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="worklogForm.note" placeholder="可留空" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="worklogSaving" @click="submitWorklog">登记</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="worklogs" size="small" border>
        <el-table-column prop="workDate" label="日期" width="120" />
        <el-table-column prop="minutes" label="分钟" width="90" />
        <el-table-column prop="userid" label="用户" width="90" />
        <el-table-column prop="note" label="备注" min-width="160" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeWorklog(row as WorklogView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-drawer v-model="commentVisible" title="任务评论" size="420px">
      <div class="comment-form">
        <el-input v-model="commentText" type="textarea" :rows="3" placeholder="写点什么…" />
        <div class="comment-actions">
          <el-input v-model="mentionInput" placeholder="提醒用户ID，逗号分隔" style="width: 190px" />
          <el-button type="primary" :loading="commentSaving" @click="submitComment">发表</el-button>
        </div>
      </div>
      <el-empty v-if="comments.length === 0" description="还没有评论" />
      <div v-for="item in comments" :key="item.id" class="comment-item">
        <div class="comment-meta">
          用户 {{ item.userid }} · {{ item.createdAt }}
        </div>
        <div class="comment-content">{{ item.content }}</div>
        <el-button link type="danger" @click="removeComment(item as CommentView)">删除</el-button>
      </div>
    </el-drawer>

    <el-drawer v-model="fileVisible" title="任务附件" size="460px">
      <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange">
        <el-button type="primary">选择文件</el-button>
        <template #tip>
          <div class="upload-tip">支持 png/jpg/gif/webp/pdf/txt/md/csv/xlsx/docx/zip，单文件 ≤ 20MB</div>
        </template>
      </el-upload>
      <el-empty v-if="attachments.length === 0" description="还没有附件" />
      <div v-for="item in attachments" :key="item.id" class="attachment-item">
        <div class="attachment-name">
          <a href="javascript:void(0)" @click="downloadAttachment(item as AttachmentView)">{{ item.originalName }}</a>
        </div>
        <div class="attachment-meta">
          {{ formatSize(item.sizeBytes) }} · 用户 {{ item.userid }} · {{ item.createdAt }}
        </div>
        <el-button link type="danger" @click="removeAttachment(item as AttachmentView)">删除</el-button>
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { AttachmentView, CommentView, MilestoneView, TaskView, WorklogView } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));

const fileVisible = ref(false);
const fileTaskId = ref(0);
const attachments = ref<AttachmentView[]>([]);

async function openFiles(row: TaskView): Promise<void> {
  fileTaskId.value = row.id;
  fileVisible.value = true;
  await loadAttachments();
}

async function loadAttachments(): Promise<void> {
  if (!fileTaskId.value) return;
  try {
    attachments.value = await client.listAttachments(fileTaskId.value);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载附件失败");
  }
}

async function onFileChange(uploadFile: { raw?: File }): Promise<void> {
  const file = uploadFile.raw;
  if (!file || !fileTaskId.value) return;
  try {
    await client.uploadAttachment(fileTaskId.value, file);
    ElMessage.success("已上传");
    await loadAttachments();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "上传失败");
  }
}

async function downloadAttachment(item: AttachmentView): Promise<void> {
  try {
    const blob = await client.downloadAttachment(item.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = item.originalName;
    link.click();
    URL.revokeObjectURL(url);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "下载失败");
  }
}

async function removeAttachment(item: AttachmentView): Promise<void> {
  try {
    await client.deleteAttachment(item.id);
    ElMessage.success("已删除");
    await loadAttachments();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

const commentVisible = ref(false);
const commentTaskId = ref(0);
const commentSaving = ref(false);
const commentText = ref("");
const mentionInput = ref("");
const comments = ref<CommentView[]>([]);

async function openComments(row: TaskView): Promise<void> {
  commentTaskId.value = row.id;
  commentText.value = "";
  mentionInput.value = "";
  commentVisible.value = true;
  await loadComments();
}

async function loadComments(): Promise<void> {
  if (!commentTaskId.value) return;
  try {
    comments.value = await client.listComments(commentTaskId.value);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载评论失败");
  }
}

async function submitComment(): Promise<void> {
  if (!commentText.value.trim()) {
    ElMessage.warning("请填写评论内容");
    return;
  }
  const mentionUserids = mentionInput.value
    .split(",")
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isFinite(value) && value > 0);
  commentSaving.value = true;
  try {
    await client.createComment(commentTaskId.value, { content: commentText.value, mentionUserids });
    commentText.value = "";
    mentionInput.value = "";
    ElMessage.success("已发表");
    await loadComments();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "发表失败");
  } finally {
    commentSaving.value = false;
  }
}

async function removeComment(item: CommentView): Promise<void> {
  try {
    await client.deleteComment(item.id);
    ElMessage.success("已删除");
    await loadComments();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

const rows = ref<TaskView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const loading = ref(false);
const saving = ref(false);
const createVisible = ref(false);
const ownerInput = ref("");
const form = reactive<{ name: string; desc: string; pLevel: number; milestoneId: number | null }>({
  name: "",
  desc: "",
  pLevel: 0,
  milestoneId: null
});
const milestones = ref<MilestoneView[]>([]);
const editingId = ref(0);

const worklogVisible = ref(false);
const worklogTaskId = ref(0);
const worklogSaving = ref(false);
const worklogs = ref<WorklogView[]>([]);
const worklogForm = reactive<{ minutes: number; workDate: string; note: string }>({
  minutes: 60,
  workDate: new Date().toISOString().slice(0, 10),
  note: ""
});

async function openWorklogs(row: TaskView): Promise<void> {
  worklogTaskId.value = row.id;
  worklogVisible.value = true;
  await loadWorklogs();
}

async function loadWorklogs(): Promise<void> {
  if (!worklogTaskId.value) return;
  try {
    worklogs.value = await client.listWorklogs(worklogTaskId.value);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载工时失败");
  }
}

async function submitWorklog(): Promise<void> {
  if (!worklogTaskId.value) return;
  worklogSaving.value = true;
  try {
    await client.createWorklog(worklogTaskId.value, {
      requestId: `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      minutes: worklogForm.minutes,
      workDate: worklogForm.workDate,
      note: worklogForm.note
    });
    ElMessage.success("已登记");
    await loadWorklogs();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "登记失败");
  } finally {
    worklogSaving.value = false;
  }
}

async function removeWorklog(row: WorklogView): Promise<void> {
  try {
    await client.deleteWorklog(row.id);
    ElMessage.success("已删除");
    await loadWorklogs();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function loadMilestones(): Promise<void> {
  if (!projectId.value) return;
  try {
    milestones.value = await client.listMilestones(projectId.value);
  } catch {
    milestones.value = [];
  }
}

async function openEdit(row: TaskView): Promise<void> {
  editingId.value = row.id;
  form.name = row.name;
  form.desc = row.desc ?? "";
  form.pLevel = row.pLevel;
  form.milestoneId = row.milestoneId ?? null;
  ownerInput.value = row.ownerUserid ? String(row.ownerUserid) : "";
  createVisible.value = true;
}

async function reload(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    const result = await client.listTasks(projectId.value, {
      page: page.value,
      size: size.value,
      keyword: keyword.value
    });
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
  editingId.value = 0;
  form.name = "";
  form.desc = "";
  form.pLevel = 0;
  form.milestoneId = null;
  ownerInput.value = "";
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  try {
    const ownerUserid = ownerInput.value ? Number(ownerInput.value) : null;
    const payload = {
      name: form.name,
      desc: form.desc,
      pLevel: form.pLevel,
      ownerUserid,
      milestoneId: form.milestoneId
    };
    if (editingId.value) {
      await client.updateTask(editingId.value, payload);
      ElMessage.success("已保存");
    } else {
      await client.createTask(projectId.value, payload);
      ElMessage.success("已创建");
    }
    createVisible.value = false;
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
  }
}

async function complete(row: TaskView): Promise<void> {
  try {
    await client.completeTask(row.id);
    ElMessage.success("已完成");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  }
}

async function remove(row: TaskView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.name}」？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteTask(row.id);
    ElMessage.success("已删除");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function goBack(): Promise<void> {
  await router.push({ name: "projects" });
}

onMounted(() => {
  void reload();
  void loadMilestones();
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
.pager {
  margin-top: 12px;
}
.comment-form {
  margin-bottom: 12px;
}
.comment-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.comment-item {
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
}
.comment-meta {
  color: #909399;
  font-size: 12px;
}
.comment-content {
  margin: 4px 0 6px;
  white-space: pre-wrap;
}
.upload-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.attachment-item {
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
}
.attachment-name a {
  color: #409eff;
  text-decoration: none;
}
.attachment-meta {
  color: #909399;
  font-size: 12px;
  margin: 4px 0;
}
</style>
