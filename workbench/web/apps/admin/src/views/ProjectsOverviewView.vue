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
      <div v-for="row in filtered" :key="row.id" class="card">
        <div class="card-head">
          <span class="name">{{ row.name }}</span>
          <span v-if="row.overdue > 0" class="overdue">{{ row.overdue }} 逾期</span>
          <el-button class="card-del" link type="danger" size="small" @click="removeProject(row)">删除</el-button>
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
import { ElMessage, ElMessageBox } from "element-plus";
import type { AdminProjectRow, UserView } from "@workbench/api";
import { client } from "../stores/auth";

const rows = ref<AdminProjectRow[]>([]);
const keyword = ref("");
const users = ref<UserView[]>([]);
const createVisible = ref(false);
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
.card-del {
  opacity: 0;
  transition: opacity 0.15s;
}
.card:hover .card-del {
  opacity: 1;
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
