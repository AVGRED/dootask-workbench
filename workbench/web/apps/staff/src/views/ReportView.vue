<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>
        <el-button link type="primary" @click="goBack">← 返回首页</el-button>
        项目报表 · {{ projectId }}
      </span>
      <span v-if="report" class="range">{{ report.from }} ~ {{ report.to }}</span>
    </el-header>
    <el-main>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="report">
        <el-row :gutter="12" class="cards">
          <el-col :span="6"><el-card shadow="never"><div class="metric">{{ report.completionPercent }}%</div><div class="metric-label">任务完成率</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="never"><div class="metric">{{ report.taskCompleted }}/{{ report.taskTotal }}</div><div class="metric-label">已完成 / 总数</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="never"><div class="metric warn">{{ report.taskOverdue }}</div><div class="metric-label">逾期任务</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="never"><div class="metric">{{ report.worklogMinutes }}</div><div class="metric-label">工时（分钟）</div></el-card></el-col>
        </el-row>

        <el-row :gutter="12" class="cards">
          <el-col :span="6"><el-card shadow="never"><div class="metric">{{ report.milestoneDone }}/{{ report.milestoneTotal }}</div><div class="metric-label">里程碑达成</div></el-card></el-col>
        </el-row>

        <div class="section-title">每日工时</div>
        <div class="chart">
          <div v-for="point in report.days" :key="point.date" class="bar-wrap" :title="`${point.date}：${point.minutes} 分钟`">
            <div class="bar" :style="{ height: `${barHeight(point.minutes)}px` }"></div>
          </div>
        </div>

        <div class="section-title">成员工时</div>
        <el-table :data="report.members" border size="small">
          <el-table-column prop="userid" label="用户" width="90" />
          <el-table-column prop="email" label="邮箱" min-width="200" />
          <el-table-column prop="minutes" label="工时（分钟）" width="130" />
          <el-table-column prop="taskCount" label="涉及任务" width="100" />
        </el-table>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { ProjectReport } from "@workbench/api";
import { client } from "../stores/auth";

const route = useRoute();
const router = useRouter();
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

async function goBack(): Promise<void> {
  await router.push({ name: "dashboard" });
}

onMounted(load);
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}
.range {
  color: #909399;
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
  color: #f56c6c;
}
.metric-label {
  color: #909399;
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
  background: #fafafa;
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
  background: #409eff;
  border-radius: 2px 2px 0 0;
  min-height: 1px;
}
</style>
