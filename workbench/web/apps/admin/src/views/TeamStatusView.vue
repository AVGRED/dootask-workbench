<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="筛选姓名 / ID / 任务" size="small" style="width: 220px" clearable />
      <span class="count">{{ filtered.length }} 人</span>
    </div>
    <div v-if="error" class="error">{{ error }}</div>
    <div class="wall">
      <div v-for="row in filtered" :key="row.userid" class="person" :class="row.status">
        <div class="person-head">
          <span class="avatar">{{ (row.nickname || "?").slice(0, 1) }}</span>
          <span class="names">
            <span class="name">{{ row.nickname || "未命名" }}</span>
            <span class="uid">#{{ row.userid }}</span>
          </span>
          <span class="badge">{{ statusText(row.status) }}</span>
        </div>
        <div v-if="row.note" class="note">“{{ row.note }}”</div>
        <div class="row">
          <span class="label">在做</span>
          <span class="value">{{ row.current_task || "—" }}</span>
        </div>
        <div class="row">
          <span class="label">日报</span>
          <span class="value" :class="{ ok: row.report_today > 0 }">
            {{ row.report_today > 0 ? "已提交" : "未提交" }}
          </span>
        </div>
        <div class="row">
          <span class="label">近 7 天</span>
          <span class="value">{{ row.week_minutes }} 分钟</span>
        </div>
      </div>
    </div>
    <div v-if="filtered.length === 0" class="empty">没有匹配的员工</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { TeamStatusRow } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<TeamStatusRow[]>([]);
const keyword = ref("");
const error = ref("");

const STATUS: Record<string, string> = {
  focus: "专注",
  idle: "空闲",
  meeting: "会议",
  away: "外出",
  leave: "请假"
};

function statusText(status: string): string {
  return STATUS[status] ?? status;
}

const filtered = computed(() => {
  const text = keyword.value.trim();
  if (!text) return rows.value;
  return rows.value.filter(
    (row) =>
      (row.nickname ?? "").includes(text) ||
      String(row.userid) === text ||
      (row.current_task ?? "").includes(text)
  );
});

async function load(): Promise<void> {
  try {
    rows.value = await client.teamStatus();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
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
.count {
  color: #909399;
  font-size: 13px;
}
.wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.person {
  border: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  border-radius: 10px;
  padding: 12px;
  background: #fff;
}
.person.focus { border-left-color: #409eff; }
.person.idle { border-left-color: #909399; }
.person.meeting { border-left-color: #e6a23c; }
.person.away { border-left-color: #67c23a; }
.person.leave { border-left-color: #f56c6c; }
.person-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.names {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.name {
  font-weight: 600;
}
.uid {
  color: #909399;
  font-size: 12px;
}
.badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #f4f4f5;
  color: #606266;
}
.note {
  margin: 8px 0;
  color: #606266;
  font-size: 13px;
}
.row {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  font-size: 13px;
}
.label {
  color: #909399;
  flex: 0 0 56px;
}
.value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.value.ok {
  color: #67c23a;
}
.empty {
  padding: 40px;
  text-align: center;
  color: #909399;
}
</style>
