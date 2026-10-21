<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">设置</span>
      <span class="page-actions"><span class="hint">修改后立即生效</span></span>
    </el-header>

    <el-main class="page-body">
      <div class="settings-grid">
        <section class="panel">
          <header class="panel-head">
            <div>
              <div class="panel-title">基本信息</div>
              <div class="panel-sub">昵称、邮箱、职位都可以自己改</div>
            </div>
            <img v-if="avatar" class="avatar" :src="`/api/v1/avatars/${avatar}`" alt="头像" />
            <span v-else class="avatar placeholder">{{ (form.nickname || "我").slice(0, 1) }}</span>
          </header>

          <label class="field">
            <span class="field-label">昵称</span>
            <el-input v-model="form.nickname" maxlength="50" show-word-limit placeholder="显示的名字" />
          </label>

          <label class="field">
            <span class="field-label">邮箱</span>
            <el-input v-model="form.email" placeholder="name@example.com" />
            <span class="field-hint">登录时也可以用邮箱，或邮箱 @ 前面那一段</span>
          </label>

          <label class="field">
            <span class="field-label">职位</span>
            <el-input v-model="form.profession" maxlength="50" placeholder="选填，比如 前端工程师" />
          </label>

          <div class="panel-foot">
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存信息</el-button>
            <span class="hint">{{ profileHint }}</span>
          </div>
        </section>

        <section class="panel">
          <header class="panel-head">
            <div>
              <div class="panel-title">修改密码</div>
              <div class="panel-sub">新密码至少 8 位；改完用新密码登录</div>
            </div>
          </header>

          <label class="field">
            <span class="field-label">原密码</span>
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="当前密码" />
          </label>

          <label class="field">
            <span class="field-label">新密码</span>
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 8 位" />
          </label>

          <label class="field">
            <span class="field-label">确认新密码</span>
            <el-input v-model="passwordForm.confirm" type="password" show-password placeholder="再输一次" />
          </label>

          <div class="panel-foot">
            <el-button type="primary" :loading="savingPassword" @click="savePassword">更新密码</el-button>
          </div>
        </section>

        <section class="panel">
          <header class="panel-head">
            <div>
              <div class="panel-title">头像</div>
              <div class="panel-sub">JPEG / PNG，不超过 2MB</div>
            </div>
          </header>
          <div class="avatar-row">
            <img v-if="avatar" class="avatar large" :src="`/api/v1/avatars/${avatar}`" alt="头像" />
            <span v-else class="avatar placeholder large">{{ (form.nickname || "我").slice(0, 1) }}</span>
            <div class="avatar-actions">
              <el-button size="small" :loading="uploading" @click="pickAvatar">上传新头像</el-button>
              <span class="hint">会显示在星图和状态墙</span>
            </div>
          </div>
          <input ref="fileInput" type="file" accept="image/*" hidden @change="onFilePicked" />
        </section>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import AppHeader from "../components/layout/AppHeader.vue";
import { client } from "../stores/auth";

const form = reactive({ nickname: "", email: "", profession: "" });
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirm: "" });
const avatar = ref("");
const savingProfile = ref(false);
const savingPassword = ref(false);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const profileHint = computed(() => (form.email ? `当前登录账号：${form.email}` : ""));

onMounted(async () => {
  try {
    const profile = await client.meProfile();
    form.nickname = profile.nickname ?? "";
    form.email = profile.email ?? "";
    form.profession = profile.profession ?? "";
    avatar.value = profile.avatar ?? "";
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "加载资料失败");
  }
});

async function saveProfile(): Promise<void> {
  if (!form.nickname.trim()) {
    ElMessage.warning("昵称不能为空");
    return;
  }
  savingProfile.value = true;
  try {
    const updated = await client.updateMeProfile({
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      profession: form.profession.trim()
    });
    form.nickname = updated.nickname;
    form.email = updated.email;
    ElMessage.success("已保存");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "保存失败");
  } finally {
    savingProfile.value = false;
  }
}

async function savePassword(): Promise<void> {
  if (passwordForm.newPassword !== passwordForm.confirm) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }
  if (passwordForm.newPassword.length < 8) {
    ElMessage.warning("新密码至少 8 位");
    return;
  }
  savingPassword.value = true;
  try {
    await client.changeMyPassword(passwordForm.oldPassword, passwordForm.newPassword);
    passwordForm.oldPassword = "";
    passwordForm.newPassword = "";
    passwordForm.confirm = "";
    ElMessage.success("密码已更新，下次请用新密码登录");
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "修改失败");
  } finally {
    savingPassword.value = false;
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
</script>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 16px;
  align-items: start;
}
.panel {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 18px;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--wb-text);
}
.panel-sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--wb-text-3);
}
.field {
  display: block;
  margin-bottom: 14px;
}
.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--wb-text-2);
}
.field-hint {
  display: block;
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--wb-text-3);
}
.panel-foot {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hint {
  font-size: 12px;
  color: var(--wb-text-3);
}
.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--wb-border);
  flex: 0 0 auto;
}
.avatar.large {
  width: 72px;
  height: 72px;
}
.avatar.placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--wb-brand-50);
  color: var(--wb-brand-700);
  font-weight: 600;
}
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
</style>
