<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">规划 · 项目 {{ projectId }}</span>
      <span class="page-actions"><el-button type="primary" @click="openCreate">新建里程碑</el-button></span>
    </el-header>
    <el-main class="page-body">
      <div class="metrics">
        <div class="metric">
          <div class="metric-label">依赖边</div>
          <div class="metric-value">{{ risk.edgeCount }}</div>
        </div>
        <div class="metric warn">
          <div class="metric-label">受阻任务</div>
          <div class="metric-value">{{ risk.blockedCount }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">无负责人</div>
          <div class="metric-value">{{ risk.unownedCount }}</div>
        </div>
        <div class="metric danger">
          <div class="metric-label">已逾期</div>
          <div class="metric-value">{{ risk.overdueCount }}</div>
        </div>
      </div>

      <el-table class="task-table" v-loading="loading" :data="milestones" border empty-text="暂无数据">
        <el-table-column prop="name" label="里程碑" min-width="180" />
        <el-table-column prop="targetAt" label="目标日期" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : 'info'">
              {{ row.status === "completed" ? "已完成" : "进行中" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="任务进度" width="140">
          <template #default="{ row }">{{ row.completedCount }} / {{ row.taskCount }}</template>
        </el-table-column>
        <el-table-column prop="reminderDays" label="提醒(天)" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row as MilestoneView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title">任务依赖</div>
      <el-table class="task-table" :data="dependencies" border size="small" empty-text="暂无数据">
        <el-table-column prop="predecessorTaskId" label="前置任务" />
        <el-table-column prop="successorTaskId" label="后置任务" />
      </el-table>

      <div class="section-title">
        本月容量
        <span v-if="capacity" class="capacity-hint">
          工作日 {{ capacity.workdays }} 天 · 每日 {{ capacity.dailyMinutes }} 分钟
        </span>
      </div>
      <el-table class="task-table" :data="capacity?.members ?? []" border size="small" empty-text="暂无数据">
        <el-table-column prop="userid" label="成员" width="100" />
        <el-table-column prop="loggedMinutes" label="已登记(分钟)" />
        <el-table-column prop="availableMinutes" label="可用(分钟)" />
        <el-table-column prop="utilizationPercent" label="利用率%" />
        <el-table-column prop="overrunMinutes" label="超出(分钟)" />
        <el-table-column prop="taskCount" label="任务数" />
      </el-table>

      <div class="section-title">排期批次</div>
      <el-form :inline="true" :model="scheduleForm">
        <el-form-item label="锚点任务">
          <el-input-number v-model="scheduleForm.anchorTaskId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="开始">
          <el-date-picker v-model="scheduleForm.anchorStart" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束">
          <el-date-picker v-model="scheduleForm.anchorEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="applySchedule">应用排期</el-button>
        </el-form-item>
      </el-form>
      <el-table class="task-table" :data="batches" border size="small" empty-text="暂无数据">
        <el-table-column prop="requestId" label="请求号" min-width="160" />
        <el-table-column prop="changeCount" label="变更数" width="90" />
        <el-table-column prop="createdAt" label="时间" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.undone ? 'info' : 'success'">{{ row.undone ? "已撤销" : "生效中" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="!row.undone" link type="danger" @click="undoSchedule(row as ScheduleBatchView)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title">
        任务模板
        <el-button link type="primary" @click="openTemplateCreate">新建模板</el-button>
      </div>
      <el-table class="task-table" :data="templates" border size="small" empty-text="暂无数据">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="revisionNo" label="版本" width="80" />
        <el-table-column prop="revisionCount" label="快照数" width="90" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="applyTemplate(row as TemplateView)">应用</el-button>
            <el-button link type="danger" @click="removeTemplate(row as TemplateView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-main>

    <el-dialog v-model="createVisible" title="新建里程碑" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="目标日期">
          <el-date-picker v-model="form.targetAt" type="date" value-format="YYYY-MM-DD" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="提醒天数">
          <el-input-number v-model="form.reminderDays" :min="-1" :max="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateVisible" title="新建模板" width="460px">
      <el-form :model="templateForm" label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="templateForm.name" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="templateForm.content" type="textarea" :rows="4" placeholder="模板步骤/检查项" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { CapacitySummary, DependencyView, MilestoneView, RiskSummaryView, ScheduleBatchView, TemplateView } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId ?? 0));

const milestones = ref<MilestoneView[]>([]);
const dependencies = ref<DependencyView[]>([]);
const risk = ref<RiskSummaryView>({ edgeCount: 0, blockedCount: 0, unownedCount: 0, overdueCount: 0 });
const capacity = ref<CapacitySummary | null>(null);
const templates = ref<TemplateView[]>([]);
const templateVisible = ref(false);
const templateForm = reactive<{ name: string; content: string }>({ name: "", content: "" });
const batches = ref<ScheduleBatchView[]>([]);
const scheduleForm = reactive<{ anchorTaskId: number; anchorStart: string; anchorEnd: string }>({
  anchorTaskId: 1,
  anchorStart: "",
  anchorEnd: ""
});
const loading = ref(false);
const saving = ref(false);
const createVisible = ref(false);
const form = reactive<{ name: string; targetAt: string; reminderDays: number }>({
  name: "",
  targetAt: "",
  reminderDays: -1
});

function openTemplateCreate(): void {
  templateForm.name = "";
  templateForm.content = "";
  templateVisible.value = true;
}

async function submitTemplate(): Promise<void> {
  saving.value = true;
  try {
    await client.createTemplate(projectId.value, { name: templateForm.name, content: templateForm.content });
    ElMessage.success("已创建模板");
    templateVisible.value = false;
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
  }
}

async function applyTemplate(row: TemplateView): Promise<void> {
  try {
    const result = await client.applyTemplate(row.id);
    ElMessage.success(`已生成任务 #${result.taskId}`);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "应用失败");
  }
}

async function applySchedule(): Promise<void> {
  if (!scheduleForm.anchorStart || !scheduleForm.anchorEnd) {
    ElMessage.warning("请选择开始与结束时间");
    return;
  }
  saving.value = true;
  try {
    const batch = await client.applySchedule(projectId.value, {
      requestId: `apply-${Date.now()}`,
      anchorTaskId: scheduleForm.anchorTaskId,
      anchorStart: scheduleForm.anchorStart,
      anchorEnd: scheduleForm.anchorEnd
    });
    ElMessage.success(`已应用，变更 ${batch.changeCount} 个任务`);
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "应用失败");
  } finally {
    saving.value = false;
  }
}

async function undoSchedule(row: ScheduleBatchView): Promise<void> {
  try {
    await client.undoSchedule(row.id);
    ElMessage.success("已撤销");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "撤销失败");
  }
}

async function removeTemplate(row: TemplateView): Promise<void> {  try {
    await ElMessageBox.confirm(`删除模板「${row.name}」？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteTemplate(row.id);
    ElMessage.success("已删除");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function reload(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    milestones.value = await client.listMilestones(projectId.value);
    templates.value = await client.listTemplates(projectId.value);
    batches.value = await client.listScheduleBatches(projectId.value);
    dependencies.value = await client.listDependencies(projectId.value);
    risk.value = await client.dependencyRisk(projectId.value);
    const now = new Date();
    const from = new Date(now.getFullYear(), now.getMonth(), 1);
    const to = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    const iso = (date: Date): string => {
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${date.getFullYear()}-${month}-${day}`;
    };
    capacity.value = await client.projectCapacity(projectId.value, iso(from), iso(to));
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  form.name = "";
  form.targetAt = "";
  form.reminderDays = -1;
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  try {
    await client.createMilestone(projectId.value, {
      name: form.name,
      targetAt: form.targetAt || null,
      reminderDays: form.reminderDays
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

async function remove(row: MilestoneView): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除里程碑「${row.name}」会解绑其任务，继续？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteMilestone(projectId.value, row.id);
    ElMessage.success("已删除");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
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
  border-bottom: 1px solid var(--wb-border);
}
.summary {
  margin-bottom: 12px;
}
.section-title {
  margin: 18px 0 8px;
  font-weight: 600;
}
</style>
