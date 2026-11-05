<template>
  <div class="meetings">
    <div class="head">
      <div>
        <h2>会议</h2>
        <p class="sub">建好会议后，参会人会收到站内通知，点「进入会议」直接跳转腾讯会议。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建会议</el-button>
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
            <div class="meta" v-if="row.project_name">项目：{{ row.project_name }}</div>
            <p v-if="row.description" class="desc">{{ row.description }}</p>
            <div class="actions">
              <el-button type="primary" size="small" @click="join(row)">进入会议</el-button>
              <el-button size="small" @click="copy(row)">复制链接</el-button>
              <el-button v-if="canManage(row)" link type="danger" size="small" @click="remove(row)">删除</el-button>
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
            <div class="meta" v-if="row.project_name">项目：{{ row.project_name }}</div>
            <p v-if="row.description" class="desc">{{ row.description }}</p>
            <div class="actions">
              <el-button size="small" type="primary" plain @click="join(row)">进入会议</el-button>
              <el-button size="small" @click="copy(row)">复制链接</el-button>
              <el-button v-if="canManage(row)" link type="danger" size="small" @click="remove(row)">删除</el-button>
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
            <div class="actions">
              <el-button v-if="canManage(row)" link type="danger" size="small" @click="remove(row)">删除</el-button>
            </div>
          </article>
        </div>
      </div>
    </template>

    <el-dialog v-model="dialogVisible" title="新建会议" width="520px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="会议主题">
          <el-input v-model="form.title" maxlength="120" placeholder="例如：周会 · 项目进度同步" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select v-model="form.projectId" clearable placeholder="不选 = 全公司会议（仅管理员）" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="时长">
          <el-select v-model="form.durationMinutes" style="width: 100%">
            <el-option :value="30" label="30 分钟" />
            <el-option :value="60" label="60 分钟" />
            <el-option :value="90" label="90 分钟" />
            <el-option :value="120" label="120 分钟" />
          </el-select>
        </el-form-item>
        <el-form-item label="会议链接">
          <el-input v-model="form.joinUrl" placeholder="腾讯会议「邀请」里的链接，或 https://meeting.tencent.com/dm/xxx" />
        </el-form-item>
        <el-form-item label="会议号">
          <el-input v-model="form.meetingCode" placeholder="选填，如 123-456-789" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">创建并通知</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { todayInBusinessZone, type MeetingView, type ProjectView } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<MeetingView[]>([]);
const projects = ref<{ id: number; name: string }[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);

const form = reactive<{
  title: string;
  projectId: number | null;
  startAt: string;
  durationMinutes: number;
  joinUrl: string;
  meetingCode: string;
  description: string;
}>({
  title: "",
  projectId: null,
  startAt: `${todayInBusinessZone()}T10:00:00`,
  durationMinutes: 60,
  joinUrl: "",
  meetingCode: "",
  description: ""
});

const now = () => Date.now();

const ongoing = computed(() =>
  rows.value.filter((row) => {
    const start = new Date(row.start_at).getTime();
    const end = start + (row.duration_minutes || 60) * 60000;
    return now() >= start && now() <= end;
  })
);

const upcoming = computed(() =>
  rows.value
    .filter((row) => new Date(row.start_at).getTime() > now())
    .sort((a, b) => new Date(a.start_at).getTime() - new Date(b.start_at).getTime())
);

const finished = computed(() =>
  rows.value.filter((row) => {
    const start = new Date(row.start_at).getTime();
    const end = start + (row.duration_minutes || 60) * 60000;
    return now() > end;
  })
);

function timeText(row: MeetingView): string {
  return (row.start_at || "").replace("T", " ").slice(0, 16);
}

function canManage(_row: MeetingView): boolean {
  return true;
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

async function loadProjects(): Promise<void> {
  try {
    const page = await client.listProjects({ page: 1, size: 100 });
    projects.value = page.list as ProjectView[];
  } catch {
    projects.value = [];
  }
}

function openCreate(): void {
  dialogVisible.value = true;
  void loadProjects();
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning("请填写会议主题");
    return;
  }
  if (!form.joinUrl.trim()) {
    ElMessage.warning("请粘贴腾讯会议链接");
    return;
  }
  saving.value = true;
  try {
    const result = await client.createMeeting({
      title: form.title.trim(),
      projectId: form.projectId,
      startAt: form.startAt,
      durationMinutes: form.durationMinutes,
      joinUrl: form.joinUrl.trim(),
      meetingCode: form.meetingCode.trim(),
      description: form.description.trim()
    });
    dialogVisible.value = false;
    ElMessage.success(`会议已创建，已通知 ${result.notified} 人`);
    form.title = "";
    form.joinUrl = "";
    form.meetingCode = "";
    form.description = "";
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
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

async function remove(row: MeetingView): Promise<void> {
  try {
    await client.deleteMeeting(row.id);
    ElMessage.success("已删除");
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

onMounted(load);
</script>

<style scoped>
.meetings {
  padding: 4px 2px 24px;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
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
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
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
  align-items: center;
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
