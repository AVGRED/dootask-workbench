<template>
  <div>
    <div v-if="error" class="error">{{ error }}</div>
    <label class="field">
      <span>日期</span>
      <input v-model="reportDate" type="date" @change="loadByDate" />
    </label>
    <label class="field">
      <span>今日完成</span>
      <textarea v-model="contentDone" placeholder="必填"></textarea>
    </label>
    <label class="field">
      <span>遇到的问题</span>
      <textarea v-model="contentBlocker" placeholder="选填"></textarea>
    </label>
    <label class="field">
      <span>投入分钟</span>
      <input v-model.number="minutes" type="number" min="0" max="1440" />
    </label>
    <button class="primary" :disabled="saving" @click="submit">{{ saving ? "提交中…" : "提交日报" }}</button>
    <p v-if="statusText" class="card-meta">当前状态：{{ statusText }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { client } from "../stores/auth";

const reportDate = ref(new Date().toISOString().slice(0, 10));
const contentDone = ref("");
const contentBlocker = ref("");
const minutes = ref(0);
const statusText = ref("");
const error = ref("");
const saving = ref(false);

async function loadByDate(): Promise<void> {
  statusText.value = "";
  contentDone.value = "";
  contentBlocker.value = "";
  minutes.value = 0;
  try {
    const report = await client.dailyReportByDate(reportDate.value);
    if (report) {
      statusText.value = report.statusText;
      contentDone.value = report.contentDone;
      contentBlocker.value = report.contentBlocker ?? "";
      minutes.value = report.minutes ?? 0;
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  }
}

async function submit(): Promise<void> {
  if (!contentDone.value.trim()) {
    error.value = "请填写今日完成";
    return;
  }
  saving.value = true;
  error.value = "";
  try {
    const saved = await client.saveDailyReport(reportDate.value, {
      contentDone: contentDone.value,
      contentBlocker: contentBlocker.value,
      minutes: minutes.value || null
    });
    statusText.value = saved.statusText;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "提交失败";
  } finally {
    saving.value = false;
  }
}

onMounted(loadByDate);
</script>
