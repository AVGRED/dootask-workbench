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
  margin-bottom: 12px;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
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
}
.name {
  font-weight: 600;
}
.overdue {
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  padding: 2px 8px;
  border-radius: 10px;
}
.meta {
  color: #909399;
  font-size: 12px;
  margin: 6px 0 10px;
}
.progress {
  height: 8px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
}
.bar {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #79bbff);
}
.stats {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}
.pct {
  font-weight: 600;
  color: #409eff;
}
.empty {
  padding: 40px;
  text-align: center;
  color: #909399;
}
</style>
