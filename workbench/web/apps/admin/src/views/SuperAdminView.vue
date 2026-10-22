<template>
  <div class="super-page">
    <div class="intro">
      <h2>超级管理</h2>
      <p>这些操作只有超级管理员可用，全部会写进审计日志。</p>
    </div>

    <div class="grid">
      <section class="card">
        <h3>代写 / 修改日报</h3>
        <el-form label-width="88px" size="small">
          <el-form-item label="员工">
            <el-select v-model="reportForm.userid" filterable placeholder="选择员工" style="width: 100%">
              <el-option v-for="u in users" :key="u.userid" :label="`${u.nickname}（${u.account ?? u.email}）`" :value="u.userid" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker v-model="reportForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="今日完成">
            <el-input v-model="reportForm.done" type="textarea" :rows="2" maxlength="2000" />
          </el-form-item>
          <el-form-item label="遇到问题">
            <el-input v-model="reportForm.blocker" type="textarea" :rows="2" maxlength="2000" />
          </el-form-item>
          <el-form-item label="明日计划">
            <el-input v-model="reportForm.plan" type="textarea" :rows="2" maxlength="2000" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy === 'report'" @click="saveReport">保存日报</el-button>
          </el-form-item>
        </el-form>
      </section>

      <section class="card">
        <h3>代建个人待办</h3>
        <el-form label-width="88px" size="small">
          <el-form-item label="员工">
            <el-select v-model="todoForm.userid" filterable placeholder="选择员工" style="width: 100%">
              <el-option v-for="u in users" :key="u.userid" :label="`${u.nickname}（${u.account ?? u.email}）`" :value="u.userid" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="todoForm.title" placeholder="例如：周五前提交周报" maxlength="200" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy === 'todo'" @click="addTodo">添加待办</el-button>
          </el-form-item>
        </el-form>

        <h3 class="mt">强制下线</h3>
        <p class="hint">让对方当前所有登录态立即失效（下次请求需要重新登录）。</p>
        <div class="inline">
          <el-select v-model="logoutUserid" filterable placeholder="选择员工" size="small" style="flex: 1">
            <el-option v-for="u in users" :key="u.userid" :label="`${u.nickname}（${u.account ?? u.email}）`" :value="u.userid" />
          </el-select>
          <el-button type="danger" size="small" :loading="busy === 'logout'" @click="forceLogout">强制下线</el-button>
        </div>
      </section>

      <section class="card">
        <h3>删除任务 / 项目</h3>
        <p class="hint">绕过负责人限制，直接软删除。请谨慎使用，操作会记录在审计日志。</p>
        <div class="inline">
          <el-input v-model.number="taskId" placeholder="任务 ID" size="small" style="flex: 1" />
          <el-button type="danger" size="small" :loading="busy === 'task'" @click="deleteTask">删除任务</el-button>
        </div>
        <div class="inline mt8">
          <el-input v-model.number="projectId" placeholder="项目 ID" size="small" style="flex: 1" />
          <el-button type="danger" size="small" :loading="busy === 'project'" @click="deleteProject">删除项目</el-button>
        </div>
      </section>
    </div>

    <section class="card audit">
      <div class="audit-head">
        <h3>审计日志</h3>
        <el-button size="small" :loading="loadingAudit" @click="loadAudit">刷新</el-button>
      </div>
      <el-table :data="auditLogs" size="small" border max-height="360">
        <el-table-column prop="created_at" label="时间" width="180">
          <template #default="{ row }">{{ (row.created_at ?? "").slice(0, 19).replace("T", " ") }}</template>
        </el-table-column>
        <el-table-column prop="operator_name" label="操作人" width="120" />
        <el-table-column prop="action" label="动作" width="170" />
        <el-table-column label="对象" width="140">
          <template #default="{ row }">{{ row.target_name || row.target_userid || "-" }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { UserView } from "@workbench/api";
import { client } from "../stores/auth";

interface AuditRow {
  id: number;
  action: string;
  target_userid: number | null;
  detail: string;
  created_at: string | null;
  operator_name: string;
  target_name: string;
}

const users = ref<UserView[]>([]);
const busy = ref("");
const loadingAudit = ref(false);
const auditLogs = ref<AuditRow[]>([]);

const today = new Date().toISOString().slice(0, 10);
const reportForm = reactive<{ userid?: number; date: string; done: string; blocker: string; plan: string }>({ userid: undefined, date: today, done: "", blocker: "", plan: "" });
const todoForm = reactive<{ userid?: number; title: string }>({ userid: undefined, title: "" });
const logoutUserid = ref<number | undefined>();
const taskId = ref<number | undefined>();
const projectId = ref<number | undefined>();

async function loadUsers(): Promise<void> {
  try {
    const page = await client.listUsers({ page: 1, size: 100 });
    users.value = page.list;
  } catch {
    users.value = [];
  }
}

async function loadAudit(): Promise<void> {
  loadingAudit.value = true;
  try {
    auditLogs.value = (await client.superAuditLogs(50)) as AuditRow[];
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载审计日志失败");
  } finally {
    loadingAudit.value = false;
  }
}

async function saveReport(): Promise<void> {
  if (!reportForm.userid) {
    ElMessage.warning("请选择员工");
    return;
  }
  if (!reportForm.done.trim()) {
    ElMessage.warning("请填写今日完成内容");
    return;
  }
  busy.value = "report";
  try {
    await client.superWriteReport({
      userid: reportForm.userid,
      reportDate: reportForm.date,
      done: reportForm.done.trim(),
      blocker: reportForm.blocker.trim(),
      plan: reportForm.plan.trim()
    });
    ElMessage.success("日报已保存");
    await loadAudit();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
  } finally {
    busy.value = "";
  }
}

async function addTodo(): Promise<void> {
  if (!todoForm.userid) {
    ElMessage.warning("请选择员工");
    return;
  }
  if (!todoForm.title.trim()) {
    ElMessage.warning("请填写待办内容");
    return;
  }
  busy.value = "todo";
  try {
    await client.superAddTodo(todoForm.userid, todoForm.title.trim());
    todoForm.title = "";
    ElMessage.success("待办已添加");
    await loadAudit();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "添加失败");
  } finally {
    busy.value = "";
  }
}

async function forceLogout(): Promise<void> {
  if (!logoutUserid.value) {
    ElMessage.warning("请选择员工");
    return;
  }
  try {
    await ElMessageBox.confirm("让对方所有登录态立即失效？", "强制下线", { type: "warning" });
  } catch {
    return;
  }
  busy.value = "logout";
  try {
    await client.superForceLogout(logoutUserid.value);
    ElMessage.success("已强制下线");
    await loadAudit();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "操作失败");
  } finally {
    busy.value = "";
  }
}

async function deleteTask(): Promise<void> {
  if (!taskId.value) {
    ElMessage.warning("请输入任务 ID");
    return;
  }
  try {
    await ElMessageBox.confirm(`删除任务 #${taskId.value}？`, "删除任务", { type: "warning" });
  } catch {
    return;
  }
  busy.value = "task";
  try {
    await client.superDeleteTask(taskId.value);
    ElMessage.success("任务已删除");
    taskId.value = undefined;
    await loadAudit();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  } finally {
    busy.value = "";
  }
}

async function deleteProject(): Promise<void> {
  if (!projectId.value) {
    ElMessage.warning("请输入项目 ID");
    return;
  }
  try {
    await ElMessageBox.confirm(`删除项目 #${projectId.value}？项目下任务会一并隐藏。`, "删除项目", { type: "warning" });
  } catch {
    return;
  }
  busy.value = "project";
  try {
    await client.superDeleteProject(projectId.value);
    ElMessage.success("项目已删除");
    projectId.value = undefined;
    await loadAudit();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  } finally {
    busy.value = "";
  }
}

onMounted(async () => {
  await loadUsers();
  await loadAudit();
});
</script>

<style scoped>
.super-page {
  padding: 4px 0 24px;
}
.intro h2 {
  margin: 0;
  font-size: 18px;
  color: var(--wb-text);
}
.intro p {
  margin: 6px 0 16px;
  font-size: 13px;
  color: var(--wb-text-3);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.card {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 16px;
}
.card h3 {
  margin: 0 0 12px;
  font-size: 14.5px;
  color: var(--wb-text);
}
.card h3.mt {
  margin-top: 18px;
}
.hint {
  margin: 0 0 10px;
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.inline {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mt8 {
  margin-top: 8px;
}
.audit-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.audit-head h3 {
  margin: 0;
}
</style>
