<template>
  <div class="empty-state" :class="size">
    <span v-if="icon" class="empty-icon">{{ icon }}</span>
    <p class="empty-title">{{ title }}</p>
    <p v-if="desc" class="empty-desc">{{ desc }}</p>
    <el-button v-if="actionText" size="small" type="primary" @click="$emit('action')">{{ actionText }}</el-button>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    desc?: string;
    icon?: string;
    actionText?: string;
    size?: "sm" | "md";
  }>(),
  { title: "暂无数据", size: "md" }
);

defineEmits<{ action: [] }>();
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 44px 20px;
  border: 1px dashed var(--wb-border-strong);
  border-radius: var(--wb-radius-lg);
  background: color-mix(in srgb, var(--wb-surface) 60%, transparent);
  text-align: center;
}
.empty-state.sm {
  padding: 22px 16px;
}
.empty-icon {
  font-size: 26px;
  line-height: 1;
  opacity: 0.8;
}
.empty-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--wb-text-2);
}
.empty-desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--wb-text-3);
  max-width: 360px;
}
</style>
