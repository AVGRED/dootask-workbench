<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>
        <el-button link type="primary" @click="goBack">← 返回项目</el-button>
        规划 · 项目 {{ projectId }}
      </span>
      <el-button type="primary" @click="openCreate">新建里程碑</el-button>
    </el-header>
    <el-main>
      <el-row :gutter="12" class="summary">
        <el-col :span="6"><el-card shadow="never">依赖边 {{ risk.edgeCount }}</el-card></el-col>
        <el-col :span="6"><el-card shadow="never">受阻任务 {{ risk.blockedCount }}</el-card></el-col>
        <el-col :span="6"><el-card shadow="never">无负责人 {{ risk.unownedCount }}</el-card></el-col>
        <el-col :span="6"><el-card shadow="never">已逾期 {{ risk.overdueCount }}</el-card></el-col>
      </el-row>

      <el-table v-loading="loading" :data="milestones" border>
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
      <el-table :data="dependencies" border size="small">
        <el-table-column prop="predecessorTaskId" label="前置任务" />
        <el-table-column prop="successorTaskId" label="后置任务" />
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
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { DependencyView, MilestoneView, RiskSummaryView } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.projectId ?? 0));

const milestones = ref<MilestoneView[]>([]);
const dependencies = ref<DependencyView[]>([]);
const risk = ref<RiskSummaryView>({ edgeCount: 0, blockedCount: 0, unownedCount: 0, overdueCount: 0 });
const loading = ref(false);
const saving = ref(false);
const createVisible = ref(false);
const form = reactive<{ name: string; targetAt: string; reminderDays: number }>({
  name: "",
  targetAt: "",
  reminderDays: -1
});

async function reload(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    milestones.value = await client.listMilestones(projectId.value);
    dependencies.value = await client.listDependencies(projectId.value);
    risk.value = await client.dependencyRisk(projectId.value);
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
.summary {
  margin-bottom: 12px;
}
.section-title {
  margin: 18px 0 8px;
  font-weight: 600;
}
</style>
