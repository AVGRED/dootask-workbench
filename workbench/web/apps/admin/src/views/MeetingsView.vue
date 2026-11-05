<template>
  <div class="meetings">
    <div class="head">
      <div>
        <h2>会议</h2>
        <p class="sub">管理员建会并指定参会人，参会人会收到站内通知，点「进入会议」直接跳转腾讯会议。</p>
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
            <div class="meta">参会人 {{ row.participant_count }} 人：{{ (row.participant_names || []).slice(0, 6).join("、") }}</div>
            <p v-if="row.description" class="desc">{{ row.description }}</p>
            <div class="actions">
              <el-button type="primary" size="small" @click="join(row)">进入会议</el-button>
              <el-button size="small" @click="copy(row)">复制链接</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
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
            <div class="meta">参会人 {{ row.participant_count }} 人：{{ (row.participant_names || []).slice(0, 6).join("、") }}</div>
            <p v-if="row.description" class="desc">{{ row.description }}</p>
            <div class="actions">
              <el-button size="small" type="primary" plain @click="join(row)">进入会议</el-button>
              <el-button size="small" @click="copy(row)">复制链接</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
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
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </div>
          </article>
        </div>
      </div>
    </template>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑会议' : '新建会议'" width="560px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="会议主题">
          <el-input v-model="form.title" maxlength="120" placeholder="例如：周会 · 项目进度同步" />
        </el-form-item>
        <el-form-item label="参会人">
          <el-select
            v-model="form.participantUserids"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择要参会的人"
            style="width: 100%"
          >
            <el-option
              v-for="user in users"
              :key="user.userid"
              :label="`${user.nickname}（${user.account || user.email}）`"
              :value="user.userid"
            />
          </el-select>
          <div class="pick-all">
            <el-button link type="primary" size="small" @click="selectAll">全选</el-button>
            <el-button link size="small" @click="selectNone">全不选</el-button>
            <el-button link size="small" @click="invertSelect">反选</el-button>
            <el-button link size="small" @click="selectStaff">仅员工</el-button>
            <el-button link size="small" @click="selectAdmins">仅管理员</el-button>
            <span class="muted">已选 {{ form.participantUserids.length }} / {{ users.length }} 人</span>
          </div>
          <div class="pick-all">
            <el-select v-model="batchProjectId" placeholder="按项目批量加入" size="small" clearable style="width: 190px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-button size="small" :loading="batchLoading" @click="addProjectMembers">加入项目成员</el-button>
            <span class="muted">只加入该项目成员，不会清掉已选的人</span>
          </div>
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
          <el-input v-model="form.joinUrl" placeholder="腾讯会议「邀请」里的链接，https://meeting.tencent.com/dm/xxx" />
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
        <el-button type="primary" :loading="saving" @click="submit">
          {{ editingId ? "保存" : "创建并通知" }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { todayInBusinessZone, type MeetingView, type UserView } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<MeetingView[]>([]);
const users = ref<UserView[]>([]);
const projects = ref<{ id: number; name: string }[]>([]);
const batchProjectId = ref<number | undefined>(undefined);
const batchLoading = ref(false);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingId = ref(0);

const form = reactive<{
  title: string;
  participantUserids: number[];
  startAt: string;
  durationMinutes: number;
  joinUrl: string;
  meetingCode: string;
  description: string;
}>({
  title: "",
  participantUserids: [],
  startAt: `${todayInBusinessZone()}T10:00:00`,
  durationMinutes: 60,
  joinUrl: "",
  meetingCode: "",
  description: ""
});

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

async function loadUsers(): Promise<void> {
  if (users.value.length) return;
  try {
    const page = await client.listUsers({ page: 1, size: 100 });
    users.value = page.list;
  } catch {
    users.value = [];
  }
}

function selectAll(): void {
  form.participantUserids = users.value.map((user) => user.userid);
}

function selectNone(): void {
  form.participantUserids = [];
}

function invertSelect(): void {
  const chosen = new Set(form.participantUserids);
  form.participantUserids = users.value.filter((user) => !chosen.has(user.userid)).map((user) => user.userid);
}

function selectStaff(): void {
  form.participantUserids = users.value.filter((user) => (user.role || "staff") !== "admin").map((user) => user.userid);
}

function selectAdmins(): void {
  form.participantUserids = users.value.filter((user) => user.role === "admin").map((user) => user.userid);
}

async function loadProjects(): Promise<void> {
  if (projects.value.length) return;
  try {
    projects.value = (await client.adminProjects()).map((row) => ({ id: row.id, name: row.name }));
  } catch {
    projects.value = [];
  }
}

async function addProjectMembers(): Promise<void> {
  if (!batchProjectId.value) {
    ElMessage.warning("请先选择项目");
    return;
  }
  batchLoading.value = true;
  try {
    const members = await client.listMembers(batchProjectId.value);
    const merged = new Set([...form.participantUserids, ...members.map((member) => member.userid)]);
    form.participantUserids = [...merged];
    ElMessage.success(`已加入 ${members.length} 位项目成员`);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加入失败");
  } finally {
    batchLoading.value = false;
  }
}

async function openCreate(): Promise<void> {
  editingId.value = 0;
  form.title = "";
  form.startAt = `${todayInBusinessZone()}T10:00:00`;
  form.durationMinutes = 60;
  form.joinUrl = "";
  form.meetingCode = "";
  form.description = "";
  batchProjectId.value = undefined;
  dialogVisible.value = true;
  await Promise.all([loadUsers(), loadProjects()]);
  selectAll();
}

async function openEdit(row: MeetingView): Promise<void> {
  editingId.value = row.id;
  form.title = row.title;
  form.startAt = row.start_at;
  form.durationMinutes = row.duration_minutes || 60;
  form.joinUrl = row.join_url;
  form.meetingCode = row.meeting_code ?? "";
  form.description = row.description ?? "";
  dialogVisible.value = true;
  batchProjectId.value = undefined;
  await Promise.all([loadUsers(), loadProjects()]);
  form.participantUserids = [...(row.participant_userids || [])];
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
  if (form.participantUserids.length === 0) {
    ElMessage.warning("请至少选择一位参会人");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      title: form.title.trim(),
      startAt: form.startAt,
      durationMinutes: form.durationMinutes,
      joinUrl: form.joinUrl.trim(),
      meetingCode: form.meetingCode.trim(),
      description: form.description.trim(),
      participantUserids: [...form.participantUserids]
    };
    if (editingId.value) {
      const result = await client.updateMeeting(editingId.value, payload);
      ElMessage.success(`已保存，新增通知 ${result.notified} 人`);
    } else {
      const result = await client.createMeeting(payload);
      ElMessage.success(`会议已创建，已通知 ${result.notified} 人`);
    }
    dialogVisible.value = false;
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
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
    await ElMessageBox.confirm(`删除会议「${row.title}」？`, "删除会议", { type: "warning" });
  } catch {
    return;
  }
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
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
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
  flex-wrap: wrap;
}
.empty {
  padding: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--wb-text-3);
}
.pick-all {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
.muted {
  font-size: 12px;
  color: var(--wb-text-3);
}
</style>
