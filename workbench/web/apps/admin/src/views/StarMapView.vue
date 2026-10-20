<template>
  <div>
    <div class="toolbar">
      <div class="intro">
        <p class="intro-title">全公司星图</p>
        <p class="intro-sub">每颗星是一个员工，连线表示共同项目；星星越大，参与项目越多。</p>
      </div>
      <span class="scope">{{ scopeText }}</span>
    </div>

    <Skeleton v-if="loading" variant="grid" :rows="2" />
    <StarMapGraph v-else-if="data" :data="data" :height="540" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { StarMapView } from "@workbench/api";
import Skeleton from "../components/common/Skeleton.vue";
import StarMapGraph from "../components/starmap/StarMapGraph.vue";
import { client } from "../stores/auth";

const data = ref<StarMapView | null>(null);
const loading = ref(false);

const scopeText = computed(() => {
  if (!data.value) return "";
  const count = data.value.nodes.length;
  const edges = data.value.edges.length;
  return `${count} 人 · ${edges} 条协作关系`;
});

onMounted(async () => {
  loading.value = true;
  try {
    data.value = await client.starMap();
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-end;
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
.scope {
  font-size: 12.5px;
  color: var(--wb-text-3);
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
  white-space: nowrap;
}
</style>
