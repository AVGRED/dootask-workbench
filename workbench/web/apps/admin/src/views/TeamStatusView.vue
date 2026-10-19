<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>员工状态墙</span>
      <span class="tools">
        <el-input v-model="keyword" placeholder="筛选姓名/邮箱/任务" size="small" style="width: 220px" clearable />
        <el-button size="small" @click="goUsers">用户管理</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </el-header>
    <el-main>
      <div v-if="error" class="error">{{ error }}</div>
      <div class="wall">
        <div v-for="row in filtered" :key="row.userid" class="person" :class="row.status">
          <div class="person-head">
            <span class="avatar">{{ (row.nickname || row.email || "?").slice(0, 1) }}</span>
            <span class="names">
              <span class="name">{{ row.nickname || row.email }}</span>
              <span class="email">{{ row.email }}</span>
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
            <span class="value" :class="{ ok: row.report_today > 0 }">{{ row.report_today > 0 ? "已提交" : "未提交" }}</span>
          </div>
          <div class="row">
            <span class="label">近 7 天</span>
            <span class="value">{{ row.week_minutes }} 分钟</span>
          </div>
        </div>
      </div>
      <div v-if="filtered.length === 0" class="empty">没有匹配的员工</div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { TeamStatusRow } from "@workbench/api";
import { client } from "../stores/auth";

const router = useRouter();
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
      (row.email ?? "").includes(text) ||
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

async function goUsers(): Promise<void> {
  await router.push({ name: "users" });
}

async function logout(): Promise<void> {
  localStorage.removeItem("workbench.admin.token");
  localStorage.removeItem("workbench.admin.user");
  await router.replace({ name: "login" });
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
.tools {
  display: flex;
  gap: 8px;
  align-items: center;
}
.wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
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
.email {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
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
