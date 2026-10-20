<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">我的日报</span>
      <span class="page-actions"></span>
    </el-header>
    <el-main class="report-main">
      <div class="report-grid">
        <section class="panel write">
          <header class="panel-head">
            <div>
              <div class="panel-title">写日报</div>
              <div class="panel-sub">{{ selectedDate }}{{ existingStatus ? " · 已提交，可覆盖" : " · 还没提交" }}</div>
            </div>
            <el-date-picker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" size="small" @change="loadByDate" />
          </header>

          <label class="field">
            <span class="field-label">今日完成 <em>必填</em></span>
            <el-input
              v-model="form.contentDone"
              type="textarea"
              :rows="5"
              maxlength="2000"
              show-word-limit
              placeholder="今天做完了什么？尽量写可验证的结果"
            />
          </label>

          <label class="field">
            <span class="field-label">遇到的问题</span>
            <el-input v-model="form.contentBlocker" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="选填" />
          </label>

          <label class="field">
            <span class="field-label">明日计划</span>
            <el-input v-model="form.contentPlan" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="选填" />
          </label>

          <div class="field">
            <span class="field-label">投入分钟</span>
            <div class="minutes-row">
              <el-input-number v-model="form.minutes" :min="0" :max="1440" :step="30" size="small" />
              <button v-for="value in QUICK_MINUTES" :key="value" class="quick" type="button" @click="form.minutes = value">
                {{ value }}
              </button>
            </div>
          </div>

          <div class="panel-foot">
            <el-button type="primary" :loading="saving" @click="save">提交日报</el-button>
            <span class="foot-hint">提交后可在右侧「近期提交」里修改</span>
          </div>
        </section>

        <aside class="panel side">
          <div class="streak">
            <div class="panel-title">近 7 天提交</div>
            <div class="streak-dots">
              <div v-for="day in streak" :key="day.date" class="streak-day">
                <span class="dot" :class="{ on: day.submitted }"></span>
                <span class="dot-label">{{ day.label }}</span>
              </div>
            </div>
            <div class="streak-hint">已提交 {{ streak.filter((day) => day.submitted).length }} / 7 天</div>
          </div>

          <div class="recent">
            <div class="panel-title">近期提交</div>
            <div v-if="reports.length === 0" class="empty">还没有提交记录</div>
            <article v-for="row in reports.slice(0, 6)" :key="row.id" class="recent-item" @click="editReport(row as DailyReportView)">
              <span class="recent-date">{{ row.reportDate.slice(5) }}</span>
              <div class="recent-body">
                <div class="recent-text">{{ row.contentDone }}</div>
                <div class="recent-meta">{{ row.minutes ?? 0 }} 分钟 · {{ row.statusText }}</div>
              </div>
              <el-button link type="danger" class="recent-del" @click.stop="remove(row as DailyReportView)">删除</el-button>
            </article>
          </div>

          <div class="weekly">
            <div class="panel-title">周报</div>
            <div v-if="weeklySummaries.length === 0" class="empty">本周还没有汇总</div>
            <div v-for="week in weeklySummaries.slice(0, 2)" :key="week.id" class="weekly-item">
              <div class="weekly-range">{{ week.weekStart }} ~ {{ week.weekEnd }}</div>
              <div class="recent-meta">{{ week.reportCount }} 篇 · 缺 {{ week.missingCount }} 天 · {{ week.totalMinutes }} 分钟</div>
            </div>
            <el-button size="small" class="regen" :loading="saving" @click="regenerate">重新生成上周周报</el-button>
          </div>
        </aside>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import type { DailyReportView, WeeklySummaryView } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const QUICK_MINUTES = [60, 120, 240, 480];
const today = new Date().toISOString().slice(0, 10);
const selectedDate = ref(today);
const existingStatus = ref("");
const reports = ref<DailyReportView[]>([]);
const weeklySummaries = ref<WeeklySummaryView[]>([]);
const saving = ref(false);
const streak = computed(() => {
  const days: { date: string; label: string; submitted: boolean }[] = [];
  const submitted = new Set(reports.value.map((row) => row.reportDate));
  for (let offset = 6; offset >= 0; offset--) {
    const date = new Date();
    date.setDate(date.getDate() - offset);
    const key = date.toISOString().slice(0, 10);
    days.push({
      date: key,
      label: ["日", "一", "二", "三", "四", "五", "六"][date.getDay()],
      submitted: submitted.has(key)
    });
  }
  return days;
});

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
  border-bottom: 1px solid var(--wb-border);
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
.report-main {
  padding: 16px 20px 32px;
}
.report-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.9fr);
  gap: 16px;
  align-items: start;
}
.panel {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 18px;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--wb-text);
}
.panel-sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.field {
  display: block;
  margin-bottom: 14px;
}
.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--wb-text-2);
}
.field-label em {
  margin-left: 6px;
  font-style: normal;
  font-size: 11px;
  color: var(--wb-danger);
}
.minutes-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.quick {
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px solid var(--wb-border);
  background: var(--wb-surface-2);
  color: var(--wb-text-2);
  font-size: 12px;
  cursor: pointer;
}
.quick:hover {
  border-color: var(--wb-brand-300);
  color: var(--wb-brand-700);
}
.panel-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}
.foot-hint {
  font-size: 12px;
  color: var(--wb-text-3);
}
.side {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.streak-dots {
  display: flex;
  justify-content: space-between;
  margin: 12px 0 8px;
}
.streak-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.streak-day .dot {
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
}
.streak-day .dot.on {
  background: linear-gradient(135deg, var(--wb-brand-400), var(--wb-brand-600));
  border-color: transparent;
}
.dot-label {
  font-size: 11px;
  color: var(--wb-text-3);
}
.streak-hint {
  font-size: 12px;
  color: var(--wb-text-3);
}
.recent-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--wb-border);
  cursor: pointer;
}
.recent-item:hover .recent-text {
  color: var(--wb-brand-700);
}
.recent-date {
  flex: 0 0 44px;
  font-size: 12px;
  color: var(--wb-text-3);
  padding-top: 2px;
}
.recent-body {
  flex: 1;
  min-width: 0;
}
.recent-text {
  font-size: 13px;
  color: var(--wb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--wb-text-3);
}
.recent-del {
  opacity: 0;
}
.recent-item:hover .recent-del {
  opacity: 1;
}
.weekly-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--wb-border);
}
.weekly-range {
  font-size: 13px;
  color: var(--wb-text);
}
.regen {
  margin-top: 10px;
}
.empty {
  padding: 16px 0;
  color: var(--wb-text-3);
  font-size: 13px;
}
@media (max-width: 1080px) {
  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
