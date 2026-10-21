<template>
  <div>
    <div class="toolbar">
      <div>
        <p class="intro-title">发布通知</p>
        <p class="intro-sub">发布后会出现在每位员工的「通知」里，未读会显示红点。</p>
      </div>
      <span class="count">{{ notices.length }} 条历史</span>
    </div>

    <section class="panel">
      <label class="field">
        <span class="field-label">标题</span>
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：本周五下午全员例会" />
      </label>
      <label class="field">
        <span class="field-label">内容</span>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="写清楚时间、地点、需要准备什么"
        />
      </label>
      <div class="actions">
        <el-button type="primary" :loading="sending" @click="publish">发布给全部员工</el-button>
        <span class="hint">当前共 {{ receiversHint }} 位员工会收到</span>
      </div>
    </section>

    <section class="panel history">
      <div class="panel-title">历史通知</div>
      <div v-if="notices.length === 0" class="empty">还没有发布过通知</div>
      <article v-for="notice in notices" :key="notice.id" class="notice">
        <header class="notice-head">
          <span class="notice-title">{{ notice.title }}</span>
          <span class="notice-meta">{{ notice.created_by_name }} · {{ (notice.created_at ?? "").slice(0, 16).replace("T", " ") }}</span>
        </header>
        <p class="notice-body">{{ notice.content }}</p>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import type { AdminNotice } from "@workbench/api";
import { client } from "../stores/auth";

const form = reactive({ title: "", content: "" });
const notices = ref<AdminNotice[]>([]);
const sending = ref(false);
const receiversHint = ref("—");

onMounted(async () => {
  await load();
  try {
    const users = await client.listUsers({ page: 1, size: 1 });
    receiversHint.value = String(users.total ?? 0);
  } catch {
    receiversHint.value = "—";
  }
});

async function load(): Promise<void> {
  try {
    notices.value = await client.adminNotices();
  } catch {
    notices.value = [];
  }
}

async function publish(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning("请填写标题");
    return;
  }
  sending.value = true;
  try {
    const result = await client.publishNotice({ title: form.title.trim(), content: form.content.trim() });
    ElMessage.success(`已发布，${result.receivers} 位员工收到`);
    form.title = "";
    form.content = "";
    await load();
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : "发布失败");
  } finally {
    sending.value = false;
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.intro-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--wb-text);
}
.intro-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--wb-text-3);
}
.count {
  font-size: 12.5px;
  color: var(--wb-text-3);
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
  white-space: nowrap;
}
.panel {
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
  box-shadow: var(--wb-shadow-1);
  padding: 18px;
  margin-bottom: 16px;
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
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint {
  font-size: 12px;
  color: var(--wb-text-3);
}
.panel-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--wb-text);
  margin-bottom: 12px;
}
.notice {
  padding: 12px 0;
  border-bottom: 1px dashed var(--wb-border);
}
.notice:last-child {
  border-bottom: none;
}
.notice-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.notice-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-text);
}
.notice-meta {
  font-size: 12px;
  color: var(--wb-text-3);
  white-space: nowrap;
}
.notice-body {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--wb-text-2);
  white-space: pre-wrap;
}
.empty {
  padding: 24px;
  text-align: center;
  color: var(--wb-text-3);
  font-size: 13px;
}
</style>
