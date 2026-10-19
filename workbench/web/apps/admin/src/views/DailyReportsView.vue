<template>
  <div>
    <div class="toolbar">
      <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" size="small" style="width: 160px" @change="load" />
      <span class="hint">已提交 {{ rows.length }} 人 · 未提交 {{ missing.length }} 人</span>
    </div>

    <div class="grid">
      <div v-for="row in rows" :key="row.id" class="card">
        <div class="card-head">
          <span class="name">{{ row.nickname || "未命名" }} <span class="uid">#{{ row.userid }}</span></span>
          <span class="minutes">{{ row.minutes ?? 0 }} 分钟</span>
        </div>
        <div class="section">
          <span class="label">今日完成</span>
          <div class="content">{{ row.content_done }}</div>
        </div>
        <div v-if="row.content_blocker" class="section">
          <span class="label blocker">阻塞</span>
          <div class="content">{{ row.content_blocker }}</div>
        </div>
        <div v-if="row.content_plan" class="section">
          <span class="label">明日计划</span>
          <div class="content">{{ row.content_plan }}</div>
        </div>
      </div>
    </div>

    <div v-if="missing.length > 0" class="missing">
      <div class="missing-title">未提交（{{ missing.length }}）</div>
      <div class="chips">
        <span v-for="item in missing" :key="item.userid" class="chip">
          {{ item.nickname || item.email }} <span class="uid">#{{ item.userid }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { AdminReportRow } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<AdminReportRow[]>([]);
const missing = ref<{ userid: number; nickname: string | null; email: string }[]>([]);
function todayLocal(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return now.getFullYear() + "-" + month + "-" + day;
}

const date = ref(todayLocal());

async function load(): Promise<void> {
  try {
    rows.value = await client.adminReports(date.value);
    missing.value = await client.adminMissingReports(date.value);
  } catch {
    rows.value = [];
    missing.value = [];
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
.card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.name {
  font-weight: 600;
}
.uid {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
.minutes {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 10px;
}
.section {
  margin-top: 8px;
}
.label {
  font-size: 12px;
  color: #909399;
}
.label.blocker {
  color: #e6a23c;
}
.content {
  white-space: pre-wrap;
  font-size: 13px;
  color: #303133;
  margin-top: 2px;
}
.missing {
  margin-top: 16px;
  background: #fff;
  border: 1px dashed #fbc4c4;
  border-radius: 10px;
  padding: 12px;
}
.missing-title {
  color: #f56c6c;
  font-weight: 600;
  margin-bottom: 8px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 12px;
  padding: 3px 10px;
  font-size: 12px;
}
</style>
