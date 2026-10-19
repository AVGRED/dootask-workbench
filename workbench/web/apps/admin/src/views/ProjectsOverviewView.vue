<template>
  <div>
    <div class="toolbar">
      <span class="hint">共 {{ rows.length }} 个项目</span>
      <el-input v-model="keyword" placeholder="筛选项目名 / 负责人" size="small" style="width: 220px" clearable />
    </div>
    <div class="grid">
      <div v-for="row in filtered" :key="row.id" class="card">
        <div class="card-head">
          <span class="name">{{ row.name }}</span>
          <span v-if="row.overdue > 0" class="overdue">{{ row.overdue }} 逾期</span>
        </div>
        <div class="meta">负责人 {{ row.owner_name || "#" + row.owner_userid }} · {{ row.members }} 名成员</div>
        <div class="progress">
          <div class="bar" :style="{ width: `${percent(row)}%` }"></div>
        </div>
        <div class="stats">
          <span>{{ row.done }}/{{ row.total }} 完成</span>
          <span class="pct">{{ percent(row) }}%</span>
        </div>
        <div class="updated-line">更新于 {{ (row.updated_at || "").slice(0, 16).replace("T", " ") || "—" }}</div>
      </div>
    </div>
    <div v-if="filtered.length === 0" class="empty">没有匹配的项目</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { AdminProjectRow } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<AdminProjectRow[]>([]);
const keyword = ref("");

const filtered = computed(() => {
  const text = keyword.value.trim();
  if (!text) return rows.value;
  return rows.value.filter(
    (row) => row.name.includes(text) || (row.owner_name ?? "").includes(text)
  );
});

function percent(row: AdminProjectRow): number {
  return row.total === 0 ? 0 : Math.round((row.done * 100) / row.total);
}

async function load(): Promise<void> {
  try {
    rows.value = await client.adminProjects();
  } catch {
    rows.value = [];
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(440px, 1fr));
  gap: 16px;
}
.card {
  position: relative;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 14px;
  padding: 20px 22px;
  min-height: 150px;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.15s, transform 0.15s;
}
.card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.name {
  font-weight: 700;
  font-size: 18px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.overdue {
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  padding: 3px 10px;
  border-radius: 10px;
  flex: 0 0 auto;
}
.meta {
  color: #909399;
  font-size: 13px;
  margin: 10px 0 16px;
}
.progress {
  height: 12px;
  background: #f0f2f5;
  border-radius: 6px;
  overflow: hidden;
}
.bar {
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, #409eff, #79bbff);
}
.stats {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 14px;
  color: #606266;
}
.pct {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}
.updated-line {
  margin-top: 10px;
  font-size: 12px;
  color: #c0c4cc;
}
.empty {
  padding: 40px;
  text-align: center;
  color: #909399;
}
</style>
