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
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { DictItemView, SettingView } from "@workbench/api";
import { client } from "../stores/auth";

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

onMounted(reload);
</script>

<style scoped>
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
