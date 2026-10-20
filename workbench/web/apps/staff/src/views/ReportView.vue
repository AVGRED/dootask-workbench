<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">项目报表 · {{ projectId }}</span>
      <span class="page-actions"><span v-if="report" class="range">{{ report.from }} ~ {{ report.to }}</span></span>
    </el-header>
    <el-main class="page-body">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="report">
        <div class="metrics">
          <div class="metric">
            <div class="metric-value">{{ report.completionPercent }}%</div>
            <div class="metric-label">任务完成率</div>
          </div>
          <div class="metric">
            <div class="metric-value">{{ report.taskCompleted }}/{{ report.taskTotal }}</div>
            <div class="metric-label">已完成 / 总数</div>
          </div>
          <div class="metric danger">
            <div class="metric-value">{{ report.taskOverdue }}</div>
            <div class="metric-label">逾期任务</div>
          </div>
          <div class="metric">
            <div class="metric-value">{{ report.worklogMinutes }}</div>
            <div class="metric-label">工时（分钟）</div>
          </div>
          <div class="metric">
            <div class="metric-value">{{ report.milestoneDone }}/{{ report.milestoneTotal }}</div>
            <div class="metric-label">里程碑达成</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">每日工时</span>
            <span class="panel-sub">{{ report.from }} ~ {{ report.to }}</span>
          </div>
          <div class="chart">
            <div
              v-for="point in report.days"
              :key="point.date"
              class="bar-wrap"
              :title="`${point.date}：${point.minutes} 分钟`"
            >
              <div class="bar" :style="{ height: `${barHeight(point.minutes)}px` }"></div>
            </div>
          </div>
          <div class="axis">
            <span>{{ report.from }}</span>
            <span>{{ report.to }}</span>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">成员工时</span>
            <span class="panel-sub">{{ report.members.length }} 人</span>
          </div>
          <el-table :data="report.members" class="task-table" empty-text="暂无数据">
            <el-table-column prop="userid" label="用户" width="90" />
            <el-table-column prop="email" label="邮箱" min-width="200" />
            <el-table-column prop="minutes" label="工时（分钟）" width="130" />
            <el-table-column prop="taskCount" label="涉及任务" width="100" />
          </el-table>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import type { ProjectReport } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId ?? 0));
const report = ref<ProjectReport | null>(null);
const loading = ref(false);

const maxMinutes = computed(() => Math.max(1, ...(report.value?.days ?? []).map((day) => day.minutes)));

function barHeight(minutes: number): number {
  return Math.round((minutes / maxMinutes.value) * 80);
}

async function load(): Promise<void> {
  if (!projectId.value) return;
  loading.value = true;
  try {
    report.value = await client.projectReport(projectId.value);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载报表失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--wb-border);
}
.range {
  color: var(--wb-text-3);
  font-size: 13px;
}
.cards {
  margin-bottom: 12px;
}
.metric {
  font-size: 26px;
  font-weight: 600;
}
.metric.warn {
  color: var(--wb-danger);
}
.metric-label {
  color: var(--wb-text-3);
  font-size: 13px;
  margin-top: 4px;
}
.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
.chart {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 90px;
  padding: 6px;
  background: var(--wb-surface-2);
  border-radius: 6px;
}
.bar-wrap {
  flex: 1;
  display: flex;
  align-items: flex-end;
  height: 100%;
}
.bar {
  width: 100%;
  background: var(--wb-brand-600);
  border-radius: 2px 2px 0 0;
  min-height: 1px;
}
.panel {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 16px;
  margin-bottom: 16px;
}
.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--wb-text);
}
.panel-sub {
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 130px;
  padding: 10px 12px 0;
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius);
}
.bar-wrap {
  flex: 1;
  display: flex;
  align-items: flex-end;
  height: 100%;
}
.bar {
  width: 100%;
  background: linear-gradient(180deg, var(--wb-brand-400), var(--wb-brand-600));
  border-radius: 4px 4px 0 0;
  min-height: 2px;
  transition: height var(--wb-duration) var(--wb-ease);
}
.bar-wrap:hover .bar {
  background: linear-gradient(180deg, var(--wb-brand-500), var(--wb-brand-700));
}
.axis {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--wb-text-3);
}
</style>
