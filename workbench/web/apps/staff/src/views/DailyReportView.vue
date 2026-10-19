<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>
        <el-button link type="primary" @click="goBack">← 返回</el-button>
        我的日报
      </span>
      <span>
        <el-date-picker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" @change="loadByDate" />
        <el-button type="primary" @click="save">提交日报</el-button>
      </span>
    </el-header>
    <el-main>
      <el-alert
        v-if="existingStatus"
        :title="`${selectedDate} 已提交（${existingStatus}），再次提交将覆盖`"
        type="info"
        :closable="false"
        show-icon
        class="hint"
      />
      <el-form :model="form" label-width="90px">
        <el-form-item label="今日完成">
          <el-input v-model="form.contentDone" type="textarea" :rows="4" placeholder="必填" />
        </el-form-item>
        <el-form-item label="遇到的问题">
          <el-input v-model="form.contentBlocker" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="明日计划">
          <el-input v-model="form.contentPlan" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="投入分钟">
          <el-input-number v-model="form.minutes" :min="0" :max="1440" />
        </el-form-item>
      </el-form>

      <div class="section-title">近期提交</div>
      <el-table :data="reports" border size="small">
        <el-table-column prop="reportDate" label="日期" width="120" />
        <el-table-column prop="contentDone" label="今日完成" min-width="240" show-overflow-tooltip />
        <el-table-column prop="minutes" label="分钟" width="90" />
        <el-table-column prop="statusText" label="状态" width="90" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="editReport(row as DailyReportView)">编辑</el-button>
            <el-button link type="danger" @click="remove(row as DailyReportView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title">周报</div>
      <el-table :data="weeklySummaries" border size="small">
        <el-table-column prop="weekStart" label="周一" width="120" />
        <el-table-column prop="weekEnd" label="周日" width="120" />
        <el-table-column prop="reportCount" label="篇数" width="90" />
        <el-table-column prop="missingCount" label="缺失" width="90" />
        <el-table-column prop="totalMinutes" label="分钟" width="90" />
        <el-table-column prop="content" label="汇总" min-width="240" show-overflow-tooltip />
      </el-table>
      <el-button class="regen" :loading="saving" @click="regenerate">重新生成上周周报</el-button>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { DailyReportView, WeeklySummaryView } from "@workbench/api";
import { client } from "../stores/auth";

const router = useRouter();
const today = new Date().toISOString().slice(0, 10);
const selectedDate = ref(today);
const existingStatus = ref("");
const reports = ref<DailyReportView[]>([]);
const weeklySummaries = ref<WeeklySummaryView[]>([]);
const saving = ref(false);
const form = reactive<{ contentDone: string; contentBlocker: string; contentPlan: string; minutes: number }>({
  contentDone: "",
  contentBlocker: "",
  contentPlan: "",
  minutes: 0
});

async function loadByDate(): Promise<void> {
  existingStatus.value = "";
  form.contentDone = "";
  form.contentBlocker = "";
  form.contentPlan = "";
  form.minutes = 0;
  try {
    const report = await client.dailyReportByDate(selectedDate.value);
    if (report) {
      existingStatus.value = report.statusText;
      form.contentDone = report.contentDone;
      form.contentBlocker = report.contentBlocker ?? "";
      form.contentPlan = report.contentPlan ?? "";
      form.minutes = report.minutes ?? 0;
    }
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载失败");
  }
}

async function loadList(): Promise<void> {
  try {
    reports.value = await client.listDailyReports();
    weeklySummaries.value = await client.listWeeklySummaries();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载失败");
  }
}

async function save(): Promise<void> {
  if (!form.contentDone.trim()) {
    ElMessage.warning("请填写今日完成");
    return;
  }
  saving.value = true;
  try {
    const saved = await client.saveDailyReport(selectedDate.value, {
      contentDone: form.contentDone,
      contentBlocker: form.contentBlocker,
      contentPlan: form.contentPlan,
      minutes: form.minutes || null
    });
    existingStatus.value = saved.statusText;
    ElMessage.success("已提交");
    await loadList();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "提交失败");
  } finally {
    saving.value = false;
  }
}

function editReport(row: DailyReportView): void {
  selectedDate.value = row.reportDate;
  void loadByDate();
}

async function remove(row: DailyReportView): Promise<void> {
  try {
    await client.deleteDailyReport(row.id);
    ElMessage.success("已删除");
    await loadList();
    await loadByDate();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function regenerate(): Promise<void> {
  saving.value = true;
  try {
    await client.regenerateWeeklySummary();
    ElMessage.success("已生成");
    await loadList();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "生成失败");
  } finally {
    saving.value = false;
  }
}

async function goBack(): Promise<void> {
  await router.push({ name: "dashboard" });
}

onMounted(async () => {
  await loadByDate();
  await loadList();
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  gap: 8px;
}
.hint {
  margin-bottom: 12px;
}
.section-title {
  margin: 18px 0 8px;
  font-weight: 600;
}
.regen {
  margin-top: 12px;
}
</style>
