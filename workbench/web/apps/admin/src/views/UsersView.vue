<template>
  <el-container class="page">
    <el-header class="page-header">
      <span>用户管理</span>
      <span>
        <el-button link type="primary" @click="goTeam">员工状态墙</el-button>
        <el-button link type="primary" @click="goSettings">系统设置</el-button>
        <el-button type="primary" @click="openCreate">新建用户</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </el-header>
    <el-main>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索邮箱或昵称" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
          <el-button type="danger" :disabled="selectedUserids.length === 0" @click="batchDelete">批量删除</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border @selection-change="onSelect">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="userid" label="ID" width="80" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="profession" label="职位" width="140" />
        <el-table-column prop="loginNum" label="登录次数" width="100" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select
              :model-value="row.role ?? 'staff'"
              size="small"
              @change="(value: string) => changeRole(row as UserView, value)"
            >
              <el-option label="员工" value="staff" />
              <el-option label="管理员" value="admin" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row as UserView)">删除</el-button>
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

    <el-dialog v-model="createVisible" title="新建用户" width="420px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="createForm.profession" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { CreateUserRequest, UserView } from "@workbench/api";
import { client, useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();

const rows = ref<UserView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const loading = ref(false);
const selectedUserids = ref<number[]>([]);
const saving = ref(false);
const createVisible = ref(false);
const createForm = reactive<CreateUserRequest>({ email: "", nickname: "", profession: "", password: "" });

function onSelect(selection: { userid: number }[]): void {
  selectedUserids.value = selection.map((item) => item.userid);
}

async function batchDelete(): Promise<void> {
  if (selectedUserids.value.length === 0) return;
  try {
    const result = await client.batchDeleteUsers(selectedUserids.value);
    ElMessage.success(`已删除 ${result.affected} 个用户`);
    selectedUserids.value = [];
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "批量删除失败");
  }
}

async function reload(): Promise<void> {
  loading.value = true;
  try {
    const result = await client.listUsers({ page: page.value, size: size.value, keyword: keyword.value });
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
  createForm.email = "";
  createForm.nickname = "";
  createForm.profession = "";
  createForm.password = "";
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  try {
    await client.createUser({ ...createForm });
    ElMessage.success("已创建");
    createVisible.value = false;
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "创建失败");
  } finally {
    saving.value = false;
  }
}

async function changeRole(row: UserView, role: string): Promise<void> {
  try {
    await client.changeRole(row.userid, role);
    row.role = role;
    ElMessage.success("角色已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "更新失败");
  }
}

async function goTeam(): Promise<void> {
  await router.push({ name: "team" });
}

async function goSettings(): Promise<void> {
  await router.push({ name: "settings" });
}

async function remove(row: UserView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${row.nickname || row.email}？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteUser(row.userid);
    ElMessage.success("已删除");
    await reload();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
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
</style>
