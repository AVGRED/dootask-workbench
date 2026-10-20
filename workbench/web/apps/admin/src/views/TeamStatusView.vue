<template>
  <div class="team-page">
    <div class="toolbar">
      <div class="filters">
        <button
          v-for="item in filters"
          :key="item.value"
          class="chip"
          :class="{ active: filter === item.value }"
          type="button"
          @click="filter = item.value"
        >
          {{ item.label }}
          <span class="chip-count">{{ countOf(item.value) }}</span>
        </button>
      </div>
      <el-input v-model="keyword" placeholder="筛选姓名 / ID / 任务" size="small" class="search" clearable />
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="wall">
      <article v-for="row in filtered" :key="row.userid" class="person">
        <div class="avatar-slot" :class="row.status">
          <img v-if="row.avatar" class="avatar-img" :src="`/api/v1/avatars/${row.avatar}`" :alt="row.nickname || ''" />
          <template v-else>
            <span class="avatar-initial">{{ (row.nickname || "?").slice(0, 1) }}</span>
            <span class="avatar-hint">3D 形象</span>
          </template>
          <span class="presence" :class="row.status"></span>
        </div>

        <div class="person-body">
          <div class="person-head">
            <span class="name">{{ row.nickname || "未命名" }}</span>
            <span class="uid">#{{ row.userid }}</span>
            <span class="status-pill" :class="row.status">{{ statusText(row.status) }}</span>
          </div>

          <p v-if="row.note" class="note">“{{ row.note }}”</p>
          <p v-else class="note muted">没写状态备注</p>

          <div class="meta">
            <div class="meta-item">
              <span class="meta-label">在做</span>
              <span class="meta-value">{{ row.current_task || "—" }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">今日日报</span>
              <span class="meta-value" :class="{ ok: row.report_today > 0 }">
                {{ row.report_today > 0 ? "已提交" : "未提交" }}
              </span>
            </div>
          </div>

          <div class="load">
            <div class="load-head">
              <span>近 7 天工时</span>
              <span class="load-value">{{ row.week_minutes }} / {{ WEEK_CAPACITY }} 分钟</span>
            </div>
            <div class="load-bar">
              <div
                class="load-fill"
                :class="{ over: row.week_minutes > WEEK_CAPACITY }"
                :style="{ width: loadWidth(row) }"
              ></div>
            </div>
          </div>
        </div>
      </article>
    </div>

    <div v-if="!loading && filtered.length === 0" class="empty">没有匹配的成员</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { TeamStatusRow } from "@workbench/api";
import { client } from "../stores/auth";

const WEEK_CAPACITY = 2400;

const rows = ref<TeamStatusRow[]>([]);
const keyword = ref("");
const filter = ref("all");
const loading = ref(false);
const error = ref("");

const filters = [
  { value: "all", label: "全部" },
  { value: "focus", label: "专注" },
  { value: "meeting", label: "会议" },
  { value: "away", label: "外出" },
  { value: "leave", label: "请假" },
  { value: "idle", label: "空闲" }
];

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

function countOf(value: string): number {
  if (value === "all") return rows.value.length;
  return rows.value.filter((row) => row.status === value).length;
}

function loadWidth(row: TeamStatusRow): string {
  const percent = Math.min(100, Math.round((row.week_minutes / WEEK_CAPACITY) * 100));
  return `${percent}%`;
}

const filtered = computed(() => {
  const text = keyword.value.trim();
  return rows.value.filter((row) => {
    if (filter.value !== "all" && row.status !== filter.value) return false;
    if (!text) return true;
    return (
      (row.nickname ?? "").includes(text) ||
      String(row.userid) === text ||
      (row.current_task ?? "").includes(text)
    );
  });
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await client.teamStatus();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.team-page {
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
.filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  color: var(--wb-text-2);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--wb-duration) var(--wb-ease);
}
.chip:hover {
  border-color: var(--wb-border-strong);
  color: var(--wb-text);
}
.chip.active {
  background: var(--wb-brand-50);
  border-color: var(--wb-brand-200);
  color: var(--wb-brand-700);
  font-weight: 600;
}
.chip-count {
  font-size: 11px;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--wb-surface-2);
  color: var(--wb-text-3);
}
.chip.active .chip-count {
  background: #fff;
  color: var(--wb-brand-700);
}
.search {
  width: 240px;
}
.error {
  color: var(--wb-danger);
  margin-bottom: 12px;
}
.wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 16px;
}
.person {
  display: flex;
  gap: 16px;
  padding: 16px;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  box-shadow: var(--wb-shadow-1);
  transition: box-shadow var(--wb-duration) var(--wb-ease),
    transform var(--wb-duration) var(--wb-ease);
}
.person:hover {
  box-shadow: var(--wb-shadow-2);
  transform: translateY(-2px);
}
.avatar-slot {
  position: relative;
  flex: 0 0 104px;
  width: 104px;
  height: 128px;
  border-radius: var(--wb-radius);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(165deg, var(--wb-brand-50), var(--wb-brand-200));
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-initial {
  font-size: 40px;
  font-weight: 700;
  color: color-mix(in srgb, var(--wb-brand-700) 70%, transparent);
}
.avatar-hint {
  position: absolute;
  bottom: 6px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 10px;
  color: color-mix(in srgb, var(--wb-brand-700) 45%, transparent);
  letter-spacing: 1px;
}
.presence {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--wb-surface);
  background: var(--wb-text-3);
}
.presence.focus { background: var(--wb-brand-600); }
.presence.meeting { background: var(--wb-warning); }
.presence.away { background: var(--wb-success); }
.presence.leave { background: var(--wb-danger); }

.person-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.person-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.name {
  font-size: 16px;
  font-weight: 700;
  color: var(--wb-text);
}
.uid {
  font-size: 12px;
  color: var(--wb-text-3);
}
.status-pill {
  margin-left: auto;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--wb-surface-2);
  color: var(--wb-text-2);
}
.status-pill.focus { background: var(--wb-brand-50); color: var(--wb-brand-700); }
.status-pill.meeting { background: color-mix(in srgb, var(--wb-warning) 12%, transparent); color: var(--wb-warning); }
.status-pill.away { background: color-mix(in srgb, var(--wb-success) 12%, transparent); color: var(--wb-success); }
.status-pill.leave { background: color-mix(in srgb, var(--wb-danger) 12%, transparent); color: var(--wb-danger); }
.note {
  margin: 0;
  font-size: 13px;
  color: var(--wb-text-2);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.note.muted {
  color: var(--wb-text-3);
}
.meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.meta-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
}
.meta-label {
  flex: 0 0 64px;
  color: var(--wb-text-3);
}
.meta-value {
  flex: 1;
  min-width: 0;
  color: var(--wb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-value.ok {
  color: var(--wb-success);
}
.load {
  margin-top: auto;
}
.load-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--wb-text-3);
  margin-bottom: 4px;
}
.load-value {
  color: var(--wb-text-2);
}
.load-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--wb-surface-2);
  overflow: hidden;
}
.load-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--wb-brand-400), var(--wb-brand-600));
  transition: width var(--wb-duration) var(--wb-ease);
}
.load-fill.over {
  background: linear-gradient(90deg, var(--wb-warning), var(--wb-danger));
}
.empty {
  padding: 48px;
  text-align: center;
  color: var(--wb-text-3);
}
</style>
