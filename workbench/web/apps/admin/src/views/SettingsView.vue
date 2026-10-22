<template>
  <el-container class="page">
    <el-header class="page-bar">
      <span class="page-title">系统设置</span>
      <span class="page-actions"></span>
    </el-header>
    <el-main class="page-body">
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        class="hint"
      />

      <div class="section-title">基本信息</div>
      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">我的资料</div>
            <div class="panel-sub">昵称、邮箱、职位都会显示在状态墙和星图上</div>
          </div>
          <div class="avatar-box">
            <img v-if="avatar" class="avatar" :src="`/api/v1/avatars/${avatar}`" alt="头像" />
            <span v-else class="avatar placeholder">{{ (profileForm.nickname || "我").slice(0, 1) }}</span>
            <div class="avatar-actions">
              <el-button size="small" :loading="uploading" @click="pickAvatar">更换头像</el-button>
            </div>
          </div>
        </div>
        <el-form label-width="90px" class="profile-form">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" maxlength="50" show-word-limit />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" placeholder="name@example.com" />
          </el-form-item>
          <el-form-item label="职位">
            <el-input v-model="profileForm.profession" maxlength="50" placeholder="选填，如 产品经理" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存信息</el-button>
          </el-form-item>
        </el-form>
        <input ref="fileInput" type="file" accept="image/*" hidden @change="onFilePicked" />
      </div>

      <div class="section-title">账号安全</div>
      <div class="panel password-panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">修改我的密码</div>
            <div class="panel-sub">只能修改当前登录账号（{{ currentAccount }}）的密码</div>
          </div>
          <el-button type="primary" :loading="changingPassword" @click="changePassword">更新密码</el-button>
        </div>
        <el-form label-width="90px" class="password-form">
          <el-form-item label="原密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="当前密码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 8 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="passwordForm.confirm" type="password" show-password placeholder="再输入一次" />
          </el-form-item>
        </el-form>
      </div>

      <div class="section-title">站点设置</div>
      <el-table v-loading="loading" :data="settings" border size="small">
        <el-table-column prop="key" label="键" width="180" />
        <el-table-column label="值" min-width="260">
          <template #default="{ row }">
            <el-input v-model="draft[row.key]" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="save(row as SettingView)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title">
        数据字典（{{ dictType }}）
        <el-button link type="primary" @click="createVisible = true">新增字典项</el-button>
      </div>
      <el-table :data="dictItems" border size="small">
        <el-table-column prop="type" label="类型" width="150" />
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="label" label="显示名" min-width="140" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? "是" : "否" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeDict(row as DictItemView)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-main>

    <el-dialog v-model="createVisible" title="新增字典项" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型">
          <el-input v-model="form.type" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.label" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDict">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { DictItemView, SettingView } from "@workbench/api";
import { client, useAuthStore } from "../stores/auth";

const settings = ref<SettingView[]>([]);
const dictItems = ref<DictItemView[]>([]);
const draft = reactive<Record<string, string>>({});
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const createVisible = ref(false);
const dictType = ref("task_priority");
const form = reactive<{ type: string; code: string; label: string; sort: number }>({
  type: "task_priority",
  code: "",
  label: "",
  sort: 0
});

const auth = useAuthStore();
const currentAccount = computed(() => auth.user?.nickname || auth.user?.username || "当前账号");
const profileForm = reactive({ nickname: "", email: "", profession: "" });
const avatar = ref("");
const savingProfile = ref(false);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

async function loadProfile(): Promise<void> {
  try {
    const profile = await client.meProfile();
    profileForm.nickname = profile.nickname ?? "";
    profileForm.email = profile.email ?? "";
    profileForm.profession = profile.profession ?? "";
    avatar.value = profile.avatar ?? "";
  } catch {
    // 未登录时忽略
  }
}

async function saveProfile(): Promise<void> {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning("昵称不能为空");
    return;
  }
  savingProfile.value = true;
  try {
    const updated = await client.updateMeProfile({
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim(),
      profession: profileForm.profession.trim()
    });
    profileForm.nickname = updated.nickname;
    profileForm.email = updated.email;
    ElMessage.success("已保存");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
  } finally {
    savingProfile.value = false;
  }
}

function pickAvatar(): void {
  fileInput.value?.click();
}

async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  uploading.value = true;
  try {
    const result = await client.uploadAvatar(file);
    avatar.value = result.avatar;
    ElMessage.success("头像已更新");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "上传失败");
  } finally {
    uploading.value = false;
    input.value = "";
  }
}
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirm: "" });
const changingPassword = ref(false);

async function changePassword(): Promise<void> {
  if (!passwordForm.oldPassword) {
    ElMessage.warning("请输入原密码");
    return;
  }
  if (passwordForm.newPassword.length < 8) {
    ElMessage.warning("新密码至少 8 位");
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirm) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }
  changingPassword.value = true;
  try {
    // 只能改当前登录账号自己的密码：走 /me/password，接口按 token 里的 userid 校验
    await client.changeMyPassword(passwordForm.oldPassword, passwordForm.newPassword);
    passwordForm.oldPassword = "";
    passwordForm.newPassword = "";
    passwordForm.confirm = "";
    ElMessage.success("密码已更新，下次请用新密码登录");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "修改失败");
  } finally {
    changingPassword.value = false;
  }
}

async function reload(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    settings.value = await client.listSettings();
    for (const item of settings.value) {
      draft[item.key] = item.value;
    }
    dictItems.value = await client.adminDicts();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败（需要管理员权限）";
  } finally {
    loading.value = false;
  }
}

async function save(row: SettingView): Promise<void> {
  try {
    const saved = await client.saveSetting(row.key, draft[row.key] ?? "", row.description);
    row.value = saved.value;
    ElMessage.success("已保存");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
  }
}

async function submitDict(): Promise<void> {
  saving.value = true;
  try {
    await client.createDict({ ...form });
    ElMessage.success("已新增");
    createVisible.value = false;
    form.code = "";
    form.label = "";
    form.sort = 0;
    dictItems.value = await client.adminDicts();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "新增失败");
  } finally {
    saving.value = false;
  }
}

async function removeDict(row: DictItemView): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除字典项「${row.label}」？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await client.deleteDict(row.id);
    dictItems.value = await client.adminDicts();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "删除失败");
  }
}

onMounted(() => {
  void loadProfile();
  void reload();
});
</script>

<style scoped>
.password-panel {
  margin-bottom: 8px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-text);
}
.panel-sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.password-form {
  max-width: 420px;
}
.profile-form {
  max-width: 420px;
}
.avatar-box {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--wb-border);
}
.avatar.placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--wb-brand-50);
  color: var(--wb-brand-700);
  font-weight: 600;
  font-size: 18px;
}
.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--wb-border);
}
.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
.hint {
  margin-bottom: 12px;
}
.page-body {
  padding: 16px 20px 28px;
}
.panel {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 16px;
  margin-bottom: 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-text);
  margin: 4px 0 10px;
}
</style>
