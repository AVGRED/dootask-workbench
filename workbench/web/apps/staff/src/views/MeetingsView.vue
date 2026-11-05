<template>
  <div class="page">
    <AppHeader />
    <main class="body">
      <div class="head">
        <div>
          <h2>会议</h2>
          <p class="sub">管理员邀请你参加的会议会显示在这里；点「进入会议」直接在腾讯会议里打开。</p>
        </div>
      </div>

      <div v-if="loading" class="empty">加载中…</div>
      <template v-else>
        <div v-if="ongoing.length" class="group">
          <div class="group-title">进行中</div>
          <div class="grid">
            <article v-for="row in ongoing" :key="row.id" class="card live">
              <div class="card-top">
                <span class="title">{{ row.title }}</span>
                <span class="tag live">进行中</span>
              </div>
              <div class="meta">{{ timeText(row) }} · 发起人 {{ row.owner_name || row.owner_userid }}</div>
              <p v-if="row.description" class="desc">{{ row.description }}</p>
              <div class="actions">
                <el-button type="primary" size="small" @click="join(row)">进入会议</el-button>
                <el-button size="small" @click="copy(row)">复制链接</el-button>
              </div>
            </article>
          </div>
        </div>

        <div class="group">
          <div class="group-title">即将开始</div>
          <div v-if="upcoming.length === 0" class="empty">暂无安排</div>
          <div class="grid">
            <article v-for="row in upcoming" :key="row.id" class="card">
              <div class="card-top">
                <span class="title">{{ row.title }}</span>
                <span class="tag">{{ row.duration_minutes }} 分钟</span>
              </div>
              <div class="meta">{{ timeText(row) }} · 发起人 {{ row.owner_name || row.owner_userid }}</div>
              <p v-if="row.description" class="desc">{{ row.description }}</p>
              <div class="actions">
                <el-button type="primary" size="small" plain @click="join(row)">进入会议</el-button>
                <el-button size="small" @click="copy(row)">复制链接</el-button>
              </div>
            </article>
          </div>
        </div>

        <div class="group">
          <div class="group-title">已结束</div>
          <div v-if="finished.length === 0" class="empty">暂无</div>
          <div class="grid">
            <article v-for="row in finished" :key="row.id" class="card muted">
              <div class="card-top">
                <span class="title">{{ row.title }}</span>
                <span class="tag">已结束</span>
              </div>
              <div class="meta">{{ timeText(row) }} · 发起人 {{ row.owner_name || row.owner_userid }}</div>
            </article>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import type { MeetingView } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const rows = ref<MeetingView[]>([]);
const loading = ref(false);

const endOf = (row: MeetingView) => new Date(row.start_at).getTime() + (row.duration_minutes || 60) * 60000;

const ongoing = computed(() =>
  rows.value.filter((row) => Date.now() >= new Date(row.start_at).getTime() && Date.now() <= endOf(row))
);
const upcoming = computed(() =>
  rows.value
    .filter((row) => new Date(row.start_at).getTime() > Date.now())
    .sort((a, b) => new Date(a.start_at).getTime() - new Date(b.start_at).getTime())
);
const finished = computed(() => rows.value.filter((row) => Date.now() > endOf(row)));

function timeText(row: MeetingView): string {
  return (row.start_at || "").replace("T", " ").slice(0, 16);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await client.listMeetings({ limit: 100 });
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载会议失败");
  } finally {
    loading.value = false;
  }
}

function join(row: MeetingView): void {
  window.open(row.join_url, "_blank", "noopener");
}

async function copy(row: MeetingView): Promise<void> {
  try {
    await navigator.clipboard.writeText(row.join_url);
    ElMessage.success("链接已复制");
  } catch {
    ElMessage.warning(row.join_url);
  }
}

onMounted(load);
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--wb-bg);
}
.body {
  max-width: 1180px;
  margin: 0 auto;
  padding: 26px 24px 48px;
}
.head {
  margin-bottom: 18px;
}
.head h2 {
  margin: 0;
  font-size: 20px;
  color: var(--wb-text);
}
.sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--wb-text-3);
}
.group {
  margin-bottom: 22px;
}
.group-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--wb-text-2);
  margin-bottom: 10px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}
.card {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card.live {
  border-color: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.14);
}
.card.muted {
  opacity: 0.72;
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.title {
  font-size: 15px;
  font-weight: 700;
  color: var(--wb-text);
}
.tag {
  font-size: 12px;
  color: var(--wb-text-3);
  border: 1px solid var(--wb-border);
  border-radius: 999px;
  padding: 1px 8px;
  white-space: nowrap;
}
.tag.live {
  color: #059669;
  border-color: #6ee7b7;
  background: #ecfdf5;
}
.meta {
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.desc {
  margin: 0;
  font-size: 13px;
  color: var(--wb-text-2);
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.empty {
  padding: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--wb-text-3);
}
</style>
