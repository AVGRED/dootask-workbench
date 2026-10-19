<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>我的项目</span>
      <span>
        <el-button type="primary" @click="openCreate">新建项目</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </el-header>
    <el-main>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索项目名称" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="项目名称" min-width="200" />
        <el-table-column prop="desc" label="描述" min-width="220" />
        <el-table-column prop="memberCount" label="成员数" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.archived ? 'info' : 'success'">{{ row.archived ? "已归档" : "进行中" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTasks(row as ProjectView)">任务</el-button>
            <el-button link type="primary" @click="openMembers(row as ProjectView)">成员</el-button>
            <el-button v-if="!row.archived" link @click="archive(row as ProjectView)">归档</el-button>
            <el-button link type="danger" @click="remove(row as ProjectView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </el-main>

    <el-dialog v-model="createVisible" title="新建项目" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.desc" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="membersVisible" :title="`成员 · ${current?.name ?? ''}`" size="420px">
      <div class="members">
        <div class="members-add">
          <el-input v-model="newUserid" placeholder="用户 ID" style="width: 160px" />
          <el-button type="primary" @click="addMember">添加成员</el-button>
        </div>
        <el-table :data="members" size="small" border>
          <el-table-column prop="userid" label="用户 ID" />
          <el-table-column label="角色">
            <template #default="{ row }">
              <el-tag :type="row.owner ? 'success' : 'info'">{{ row.owner ? "负责人" : "成员" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="!row.owner" link type="danger" @click="dropMember(row.userid)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { MemberView, ProjectView } from "@workbench/api";
import { client, useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();

const rows = ref<ProjectView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const loading = ref(false);
const saving = ref(false);
const createVisible = ref(false);
const form = reactive({ name: "", desc: "" });

const membersVisible = ref(false);
const current = ref<ProjectView | null>(null);
const members = ref<MemberView[]>([]);
const newUserid = ref("");

async function reload(): Promise<void> {
  loading.value = true;
  try {
    const result = await client.listProjects({ page: page.value, size: size.value, keyword: keyword.value });
    rows.value = result.list;
    total.value = result.total;
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

function onPageChange(next: number): void {
  page.value = next;
  void reload();
}

function openCreate(): void {
  form.name = "";
  form.desc = "";
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  try {
    await client.createProject({ name: form.name, desc: form.desc });
    ElMessage.success("已创建");
    createVisible.value = false;
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
  }
}

async function archive(row: ProjectView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定归档项目「${row.name}」？`, "归档确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.archiveProject(row.id);
    ElMessage.success("已归档");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "归档失败");
  }
}

async function remove(row: ProjectView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」？该操作不可恢复。`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteProject(row.id);
    ElMessage.success("已删除");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

async function openMembers(row: ProjectView): Promise<void> {
  current.value = row;
  membersVisible.value = true;
  await loadMembers(row.id);
}

async function openTasks(row: ProjectView): Promise<void> {
  await router.push({ name: "tasks", params: { projectId: row.id } });
}

async function loadMembers(projectId: number): Promise<void> {
  try {
    members.value = await client.listMembers(projectId);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载成员失败");
  }
}

async function addMember(): Promise<void> {
  const userid = Number(newUserid.value);
  if (!current.value || !Number.isFinite(userid) || userid <= 0) {
    ElMessage.warning("请输入有效的用户 ID");
    return;
  }
  try {
    await client.addMember(current.value.id, userid);
    newUserid.value = "";
    await loadMembers(current.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "添加失败");
  }
}

async function dropMember(userid: number): Promise<void> {
  if (!current.value) return;
  try {
    await client.removeMember(current.value.id, userid);
    await loadMembers(current.value.id);
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "移除失败");
  }
}

async function logout(): Promise<void> {
  auth.logout();
  await router.replace({ name: "login" });
}

onMounted(() => {
  void reload();
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
}
.members-add {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
