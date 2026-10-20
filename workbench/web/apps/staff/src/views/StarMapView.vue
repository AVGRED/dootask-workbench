<template>
  <el-container class="page">
    <AppHeader />
    <el-header class="page-bar">
      <span class="page-title">星图</span>
      <span class="page-actions">
        <span class="scope">{{ scopeText }}</span>
      </span>
    </el-header>

    <el-main class="page-body">
      <div class="intro">
        <p class="intro-title">你与合作者的星图</p>
        <p class="intro-sub">每颗星是一个同事，连线表示一起做过同一个项目；星星越大，参与的项目越多。</p>
      </div>

      <Skeleton v-if="loading" variant="grid" :rows="2" />
      <StarMapGraph v-else-if="data" :data="data" :height="480" />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { StarMapView } from "@workbench/api";
import AppHeader from "../components/layout/AppHeader.vue";
import Skeleton from "../components/common/Skeleton.vue";
import StarMapGraph from "../components/starmap/StarMapGraph.vue";
import { client } from "../stores/auth";

const data = ref<StarMapView | null>(null);
const loading = ref(false);

const scopeText = computed(() => {
  if (!data.value) return "";
  const count = data.value.nodes.length;
  return data.value.scope === "all" ? `全公司 · ${count} 人` : `我的合作者 · ${count} 人`;
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
.scope {
  font-size: 12.5px;
  color: var(--wb-text-3);
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--wb-surface-2);
  border: 1px solid var(--wb-border);
}
.intro {
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
</style>
