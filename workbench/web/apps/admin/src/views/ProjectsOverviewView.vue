<template>
  <div>
    <div class="toolbar">
      <span class="hint">共 {{ rows.length }} 个项目</span>
      <el-input v-model="keyword" placeholder="筛选项目名 / 负责人" size="small" style="width: 220px" clearable />
      <div class="toolbar-right">
        <el-button type="primary" size="small" @click="openCreate">新建项目</el-button>
      </div>
    </div>
    <div class="stats">
      <div class="stat-card">
        <span class="stat-label">项目总数</span>
        <span class="stat-value">{{ rows.length }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">平均完成度</span>
        <span class="stat-value">{{ avgPercent }}%</span>
      </div>
      <div class="stat-card warn">
        <span class="stat-label">存在逾期</span>
        <span class="stat-value">{{ overdueProjects }}</span>
      </div>
    </div>

    <div class="grid">
      <div v-for="row in filtered" :key="row.id" class="card" @click="openProject(row)">
        <div class="card-head">
          <span class="name">{{ row.name }}</span>
          <span v-if="row.overdue > 0" class="overdue">{{ row.overdue }} 逾期</span>
          <span class="card-actions">
            <el-button class="card-act" link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-button class="card-act" link type="primary" size="small" @click.stop="openMembers(row)">成员</el-button>
            <el-button class="card-del" link type="danger" size="small" @click.stop="removeProject(row)">删除</el-button>
          </span>
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

    <el-dialog v-model="editVisible" title="编辑项目" width="480px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="项目名称">
          <el-input v-model="editForm.name" maxlength="255" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.desc" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="membersVisible" :title="`成员 · ${membersProject?.name ?? ''}`" width="460px">
      <div class="member-add">
        <el-select v-model="memberToAdd" filterable placeholder="添加成员" size="small" style="flex: 1">
          <el-option
            v-for="user in candidateUsers"
            :key="user.userid"
            :label="`${user.nickname}（${user.account || user.email}）`"
            :value="user.userid"
          />
        </el-select>
        <el-button type="primary" size="small" :loading="memberBusy" @click="addMember">添加</el-button>
      </div>
      <div v-if="members.length === 0" class="empty">还没有成员</div>
      <div v-for="member in members" :key="member.userid" class="member-row">
        <span>{{ member.nickname || member.userid }}</span>
        <span class="member-role">{{ member.role || "member" }}</span>
        <el-button link type="danger" size="small" @click="removeMember(member.userid)">移除</el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新建项目" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="项目名称">
          <el-input v-model="form.name" maxlength="255" placeholder="例如：官网改版" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.desc" type="textarea" :rows="3" maxlength="500" placeholder="项目目标、范围（可选）" />
        </el-form-item>
        <el-form-item label="成员">
          <el-select
            v-model="form.memberUserids"
            multiple
            filterable
            placeholder="选择项目成员（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="user in users"
              :key="user.userid"
              :label="`${user.nickname}（${user.account || user.email}）`"
              :value="user.userid"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { AdminProjectRow, UserView } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<AdminProjectRow[]>([]);
const router = useRouter();

function openProject(row: AdminProjectRow): void {
  void router.push({ name: "project", params: { id: row.id } });
}
const keyword = ref("");
const users = ref<UserView[]>([]);
const createVisible = ref(false);
const editVisible = ref(false);
const editForm = reactive<{ id: number; name: string; desc: string }>({ id: 0, name: "", desc: "" });
const membersVisible = ref(false);
const membersProject = ref<AdminProjectRow | null>(null);
const members = ref<{ userid: number; nickname?: string; role?: string }[]>([]);
const memberToAdd = ref<number | undefined>();
const memberBusy = ref(false);
const saving = ref(false);
const form = reactive<{ name: string; desc: string; memberUserids: number[] }>({
  name: "",
  desc: "",
  memberUserids: []
});

async function loadUsers(): Promise<void> {
  try {
    const page = await client.listUsers({ page: 1, size: 100 });
    users.value = page.list;
  } catch {
    users.value = [];
  }
}

function openCreate(): void {
  form.name = "";
  form.desc = "";
  form.memberUserids = [];
  createVisible.value = true;
  if (users.value.length === 0) void loadUsers();
}

async function submitCreate(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning("请填写项目名称");
    return;
  }
  saving.value = true;
  try {
    const project = await client.createProject({ name: form.name.trim(), desc: form.desc.trim() });
    for (const userid of form.memberUserids) {
      try {
        await client.addMember(project.id, userid);
      } catch {
        // 单个成员添加失败不阻塞整个创建流程
      }
    }
    createVisible.value = false;
    ElMessage.success("项目已创建");
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
  }
}

function openEdit(row: AdminProjectRow): void {
  editForm.id = row.id;
  editForm.name = row.name;
  editForm.desc = "";
  editVisible.value = true;
}

async function submitEdit(): Promise<void> {
  if (!editForm.name.trim()) {
    ElMessage.warning("请填写项目名称");
    return;
  }
  saving.value = true;
  try {
    await client.updateProject(editForm.id, { name: editForm.name.trim(), desc: editForm.desc.trim() });
    editVisible.value = false;
    ElMessage.success("项目已更新");
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "更新失败");
  } finally {
    saving.value = false;
  }
}

const candidateUsers = computed(() => {
  const joined = new Set(members.value.map((member) => member.userid));
  return users.value.filter((user) => !joined.has(user.userid));
});

async function openMembers(row: AdminProjectRow): Promise<void> {
  membersProject.value = row;
  membersVisible.value = true;
  memberToAdd.value = undefined;
  await loadMembers(row.id);
  if (users.value.length === 0) await loadUsers();
}

async function loadMembers(projectId: number): Promise<void> {
  try {
    members.value = (await client.listMembers(projectId)) as { userid: number; nickname?: string; role?: string }[];
  } catch {
    members.value = [];
  }
}

async function addMember(): Promise<void> {
  if (!memberToAdd.value || !membersProject.value) return;
  memberBusy.value = true;
  try {
    await client.addMember(membersProject.value.id, memberToAdd.value);
    memberToAdd.value = undefined;
    await loadMembers(membersProject.value.id);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "添加失败");
  } finally {
    memberBusy.value = false;
  }
}

async function removeMember(userid: number): Promise<void> {
  if (!membersProject.value) return;
  try {
    await client.removeMember(membersProject.value.id, userid);
    await loadMembers(membersProject.value.id);
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "移除失败");
  }
}

async function removeProject(row: AdminProjectRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除项目「${row.name}」？项目下任务会一并隐藏。`, "删除确认", {
      type: "warning"
    });
  } catch {
    return;
  }
  try {
    await client.deleteProject(row.id);
    ElMessage.success("已删除");
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

const filtered = computed(() => {
  const text = keyword.value.trim();
  if (!text) return rows.value;
  return rows.value.filter(
    (row) => row.name.includes(text) || (row.owner_name ?? "").includes(text)
  );
});

const avgPercent = computed(() => {
  if (rows.value.length === 0) return 0;
  const total = rows.value.reduce((sum, row) => sum + percent(row), 0);
  return Math.round(total / rows.value.length);
});

const overdueProjects = computed(() => rows.value.filter((row) => row.overdue > 0).length);

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
  margin-bottom: 16px;
}
.toolbar-right {
  margin-left: auto;
}
.card-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}
.card:hover .card-actions {
  opacity: 1;
}
.member-add {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--wb-border);
  font-size: 13px;
}
.member-role {
  margin-left: auto;
  font-size: 12px;
  color: var(--wb-text-3);
}
.hint {
  color: var(--wb-text-3);
  font-size: 13px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background: var(--wb-surface);
  box-shadow: var(--wb-shadow-1);
}
.stat-label {
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--wb-text);
}
.stat-card.warn .stat-value {
  color: var(--wb-warning);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(440px, 1fr));
  gap: 16px;
}
.card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 150px;
  padding: 20px 22px;
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.name {
  font-weight: 700;
  font-size: 18px;
  line-height: 1.3;
  color: var(--wb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.overdue {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--wb-danger);
  background: color-mix(in srgb, var(--wb-danger) 12%, transparent);
  padding: 3px 10px;
  border-radius: 999px;
}
.meta {
  color: var(--wb-text-3);
  font-size: 13px;
  margin: 10px 0 16px;
}
.progress {
  height: 12px;
  background: var(--wb-surface-2);
  border-radius: 6px;
  overflow: hidden;
}
.bar {
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--wb-brand-400), var(--wb-brand-600));
}
.stats-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 14px;
  color: var(--wb-text-2);
}
.pct {
  font-size: 20px;
  font-weight: 700;
  color: var(--wb-brand-700);
}
.updated-line {
  margin-top: 10px;
  font-size: 12px;
  color: var(--wb-text-3);
}
.empty {
  padding: 40px;
  text-align: center;
  color: var(--wb-text-3);
}
</style>
