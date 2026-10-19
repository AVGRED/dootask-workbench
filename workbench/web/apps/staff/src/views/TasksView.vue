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
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openWorklogs(row as TaskView)">工时</el-button>
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
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { TaskView, WorklogView } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));

const rows = ref<TaskView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const loading = ref(false);
const saving = ref(false);
const createVisible = ref(false);
const ownerInput = ref("");
const form = reactive<{ name: string; desc: string; pLevel: number }>({ name: "", desc: "", pLevel: 0 });

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
  form.name = "";
  form.desc = "";
  form.pLevel = 0;
  ownerInput.value = "";
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  try {
    const ownerUserid = ownerInput.value ? Number(ownerInput.value) : null;
    await client.createTask(projectId.value, {
      name: form.name,
      desc: form.desc,
      pLevel: form.pLevel,
      ownerUserid
    });
    ElMessage.success("已创建");
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
</style>
