<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="筛选姓名 / ID / 任务" size="small" style="width: 220px" clearable />
      <span class="count">{{ filtered.length }} 人</span>
    </div>
    <div v-if="error" class="error">{{ error }}</div>
    <div class="wall">
      <div v-for="row in filtered" :key="row.userid" class="person" :class="row.status">
        <div class="avatar-slot" :class="row.status">
          <span class="avatar-initial">{{ (row.nickname || "?").slice(0, 1) }}</span>
          <span class="avatar-hint">3D 形象</span>
        </div>
        <div class="person-body">
          <div class="person-head">
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
  margin-bottom: 14px;
}
.count {
  color: #909399;
  font-size: 13px;
}
.wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 16px;
}
.person {
  display: flex;
  gap: 14px;
  border: 1px solid #ebeef5;
  border-left: 5px solid #909399;
  border-radius: 14px;
  padding: 16px;
  background: #fff;
  min-height: 168px;
  transition: box-shadow 0.15s, transform 0.15s;
}
.person:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.person.focus { border-left-color: #409eff; }
.person.idle { border-left-color: #909399; }
.person.meeting { border-left-color: #e6a23c; }
.person.away { border-left-color: #67c23a; }
.person.leave { border-left-color: #f56c6c; }

/* 左侧 3D 虚拟形象位（预留） */
.avatar-slot {
  position: relative;
  flex: 0 0 104px;
  width: 104px;
  height: 128px;
  border-radius: 16px;
  background: linear-gradient(165deg, #dbe8fb 0%, #c3d8f4 55%, #a8c6ee 100%);
  box-shadow: inset 0 -18px 26px rgba(64, 158, 255, 0.18), inset 0 2px 0 rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.avatar-slot::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 8px;
  height: 10px;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(64, 158, 255, 0.28), transparent 70%);
}
.avatar-slot.focus { background: linear-gradient(165deg, #dbe8fb, #a8c6ee); }
.avatar-slot.idle { background: linear-gradient(165deg, #eef0f4, #d3d8e0); }
.avatar-slot.meeting { background: linear-gradient(165deg, #fbeed8, #efd0a1); }
.avatar-slot.away { background: linear-gradient(165deg, #dcf1e1, #a9d5b6); }
.avatar-slot.leave { background: linear-gradient(165deg, #fbdee8, #eeb2c6); }
.avatar-initial {
  font-size: 42px;
  font-weight: 700;
  color: rgba(48, 49, 51, 0.55);
  text-shadow: 0 2px 0 rgba(255, 255, 255, 0.6);
  z-index: 1;
}
.avatar-hint {
  position: absolute;
  bottom: 4px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 10px;
  color: rgba(48, 49, 51, 0.35);
  letter-spacing: 1px;
}

.person-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.person-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.names {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.name {
  font-weight: 600;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.uid {
  color: #909399;
  font-size: 12px;
}
.badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  background: #f4f4f5;
  color: #606266;
  flex: 0 0 auto;
}
.note {
  margin: 10px 0 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.row {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 13px;
}
.label {
  color: #909399;
  flex: 0 0 64px;
}
.value {
  flex: 1;
  min-width: 0;
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
