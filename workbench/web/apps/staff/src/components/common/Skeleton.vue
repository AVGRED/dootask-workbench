<template>
  <div class="skeleton-wrap">
    <div v-if="variant === 'table'" class="skeleton-table">
      <div v-for="row in rows" :key="row" class="skeleton-row">
        <span class="bar w-16"></span>
        <span class="bar w-40"></span>
        <span class="bar w-24"></span>
        <span class="bar w-12"></span>
      </div>
    </div>

    <div v-else-if="variant === 'grid'" class="skeleton-grid">
      <div v-for="item in rows" :key="item" class="skeleton-card" :class="{ wide: item % 5 === 1 }"></div>
    </div>

    <div v-else class="skeleton-lines">
      <span v-for="item in rows" :key="item" class="bar" :class="item === 1 ? 'w-40' : item % 3 === 0 ? 'w-24' : 'w-full'"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    rows?: number;
    variant?: "lines" | "table" | "grid";
  }>(),
  { rows: 4, variant: "lines" }
);
</script>

<style scoped>
.skeleton-wrap {
  width: 100%;
}
.bar {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--wb-surface-2) 25%, var(--wb-surface) 37%, var(--wb-surface-2) 63%);
  background-size: 400% 100%;
  animation: wb-shimmer 1.4s ease infinite;
}
.skeleton-lines {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 0;
}
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.skeleton-card {
  height: 130px;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background: linear-gradient(90deg, var(--wb-surface) 25%, var(--wb-surface-2) 37%, var(--wb-surface) 63%);
  background-size: 400% 100%;
  animation: wb-shimmer 1.4s ease infinite;
}
.skeleton-card.wide {
  grid-column: span 2;
}
.skeleton-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-lg);
}
.skeleton-row {
  display: grid;
  grid-template-columns: 60px 1fr 120px 80px;
  gap: 12px;
  align-items: center;
}
.w-12 { width: 44px; }
.w-16 { width: 60px; }
.w-24 { width: 96px; }
.w-40 { width: 160px; }
.w-full { width: 100%; }
@keyframes wb-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
</style>
