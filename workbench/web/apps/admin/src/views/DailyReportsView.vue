<template>
  <div class="report-page">
    <div class="toolbar">
      <div class="stats">
        <div class="stat">
          <span class="stat-label">已提交</span>
          <span class="stat-value ok">{{ rows.length }}</span>
        </div>
        <div class="stat">
          <span class="stat-label">未提交</span>
          <span class="stat-value warn">{{ missing.length }}</span>
        </div>
        <div class="stat">
          <span class="stat-label">提交率</span>
          <span class="stat-value">{{ coverage }}%</span>
        </div>
      </div>
      <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" size="small" class="date" @change="load" />
    </div>

    <div v-if="rows.length > 0" class="grid">
      <article v-for="row in rows" :key="row.id" class="card">
        <header class="card-head">
          <span class="avatar">{{ (row.nickname || "?").slice(0, 1) }}</span>
          <div class="who">
            <span class="name">{{ row.nickname || "未命名" }}</span>
            <span class="uid">#{{ row.userid }}</span>
          </div>
          <span class="minutes">{{ row.minutes ?? 0 }} 分钟</span>
        </header>

        <div class="section">
          <span class="label">今日完成</span>
          <p class="content">{{ row.content_done }}</p>
        </div>
        <div v-if="row.content_blocker" class="section">
          <span class="label blocker">阻塞</span>
          <p class="content">{{ row.content_blocker }}</p>
        </div>
        <div v-if="row.content_plan" class="section">
          <span class="label">明日计划</span>
          <p class="content">{{ row.content_plan }}</p>
        </div>
      </article>
    </div>
    <div v-else class="empty">这一天还没有人提交日报</div>

    <section v-if="missing.length > 0" class="missing">
      <div class="missing-head">
        <span class="missing-title">未提交 · {{ missing.length }} 人</span>
      </div>
      <div class="chips">
        <span v-for="item in missing" :key="item.userid" class="chip">
          <span class="chip-avatar">{{ (item.nickname || item.email).slice(0, 1) }}</span>
          {{ item.nickname || item.email }}
          <span class="uid">#{{ item.userid }}</span>
        </span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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

const coverage = computed(() => {
  const total = rows.value.length + missing.value.length;
  return total === 0 ? 0 : Math.round((rows.value.length / total) * 100);
});

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
.report-page {
  padding-bottom: 24px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stats {
  display: flex;
  gap: 12px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 104px;
  padding: 12px 16px;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  box-shadow: var(--wb-shadow-1);
}
.stat-label {
  font-size: 12px;
  color: var(--wb-text-3);
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--wb-text);
}
.stat-value.ok {
  color: var(--wb-success);
}
.stat-value.warn {
  color: var(--wb-warning);
}
.date {
  width: 160px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  box-shadow: var(--wb-shadow-1);
  transition: box-shadow var(--wb-duration) var(--wb-ease),
    transform var(--wb-duration) var(--wb-ease);
}
.card:hover {
  box-shadow: var(--wb-shadow-2);
  transform: translateY(-2px);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--wb-brand-50);
  color: var(--wb-brand-700);
  font-weight: 600;
  font-size: 14px;
}
.who {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.name {
  font-weight: 600;
  color: var(--wb-text);
}
.uid {
  font-size: 12px;
  color: var(--wb-text-3);
}
.minutes {
  font-size: 12px;
  color: var(--wb-brand-700);
  background: var(--wb-brand-50);
  border-radius: 999px;
  padding: 3px 10px;
}
.section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.label {
  font-size: 11.5px;
  color: var(--wb-text-3);
  letter-spacing: 0.2px;
}
.label.blocker {
  color: var(--wb-warning);
}
.content {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--wb-text);
  white-space: pre-wrap;
  word-break: break-word;
}
.missing {
  margin-top: 18px;
  padding: 16px;
  border-radius: var(--wb-radius-lg);
  border: 1px dashed color-mix(in srgb, var(--wb-danger) 35%, var(--wb-border));
  background: color-mix(in srgb, var(--wb-danger) 4%, var(--wb-surface));
}
.missing-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-danger);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  font-size: 12.5px;
  color: var(--wb-text-2);
}
.chip-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--wb-danger) 12%, transparent);
  color: var(--wb-danger);
  font-size: 11px;
  font-weight: 600;
}
.empty {
  padding: 44px;
  text-align: center;
  color: var(--wb-text-3);
  border: 1px dashed var(--wb-border-strong);
  border-radius: var(--wb-radius-lg);
}
</style>
