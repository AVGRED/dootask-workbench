<template>
  <div ref="bentoRef" class="bento" :style="{ height: `${gridHeight}px` }">
    <div
      v-for="item in visibleLayout"
      :key="item.id"
      class="tile"
      :class="{ dragging: drag?.id === item.id }"
      :style="tileStyle(item)"
      @pointerdown="onTileDown($event, item)"
    >
      <div class="tile-head">
        <span class="tile-title">{{ titleOf(item.id) }}</span>
        <span class="tile-tools">
          <span class="remove" title="移除板块" @click.stop="emit('remove', item.id)">×</span>
        </span>
      </div>
      <div class="tile-body">
        <WidgetContent
          :id="item.id"
          :data="data"
          @open-tasks="(projectId) => emit('open-tasks', projectId)"
          @open-project="(project) => emit('open-project', project)"
          @open-board="(project) => emit('open-board', project)"
          @open-gantt="(project) => emit('open-gantt', project)"
          @open-board-id="(projectId) => emit('open-board-id', projectId)"
          @go-report="emit('go-report')"
        />
      </div>
      <div class="resize" title="拖动调整大小"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { LayoutItem, WidgetDef } from "@workbench/api";
import WidgetContent, { type ProjectBlock } from "./WidgetContent.vue";
import { ROW_HEIGHT, buildColorIndex, gridHeightOf, tileStyleOf } from "../../composables/bentoGrid";

const props = defineProps<{
  layout: LayoutItem[];
  catalog: WidgetDef[];
  data: Record<string, unknown>;
}>();

const emit = defineEmits<{
  save: [layout: LayoutItem[]];
  remove: [id: string];
  "open-tasks": [projectId: number];
  "open-project": [project: ProjectBlock | undefined];
  "open-board": [project: ProjectBlock | undefined];
  "open-gantt": [project: ProjectBlock | undefined];
  "open-board-id": [projectId: number];
  "go-report": [];
}>();

const bentoRef = ref<HTMLElement | null>(null);
const drag = ref<{
  id: string;
  startX: number;
  startY: number;
  startCol: number;
  startRow: number;
  w: number;
  h: number;
  mode: "move" | "resize";
} | null>(null);

const colorIndex = computed(() => buildColorIndex(props.layout));
// 工时板块已下线：旧布局里残留的 capacity 不渲染
const visibleLayout = computed(() => props.layout.filter((item: LayoutItem) => item.id !== "capacity"));

const gridHeight = computed(() => gridHeightOf(props.layout));

function titleOf(id: string): string {
  if (id.startsWith("project:")) {
    const project = props.data[id] as ProjectBlock | undefined;
    return project?.name ?? "项目";
  }
  return props.catalog.find((widget) => widget.id === id)?.title ?? id;
}

function tileStyle(item: LayoutItem): Record<string, string> {
  return tileStyleOf(item, colorIndex.value);
}

function onTileDown(event: PointerEvent, item: LayoutItem): void {
  const target = event.target as HTMLElement;
  if (target.classList.contains("resize")) {
    startDrag(event, item, "resize");
  } else if (target.closest(".tile-head")) {
    startDrag(event, item, "move");
  }
}

function startDrag(event: PointerEvent, item: LayoutItem, mode: "move" | "resize"): void {
  event.preventDefault();
  drag.value = {
    id: item.id,
    startX: event.clientX,
    startY: event.clientY,
    startCol: item.x,
    startRow: item.y,
    w: item.w,
    h: item.h,
    mode
  };
  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onUp, { once: true });
  window.addEventListener("pointercancel", onCancel, { once: true });
}

function onMove(event: PointerEvent): void {
  if (!drag.value) return;
  const item = props.layout.find((entry) => entry.id === drag.value!.id);
  if (!item) return;
  const gridWidth = bentoRef.value?.clientWidth ?? window.innerWidth;
  const colWidth = gridWidth / 12;
  const dx = Math.round((event.clientX - drag.value.startX) / colWidth);
  const dy = Math.round((event.clientY - drag.value.startY) / ROW_HEIGHT);
  if (drag.value.mode === "move") {
    item.x = Math.min(Math.max(drag.value.startCol + dx, 0), 12 - item.w);
    item.y = Math.max(drag.value.startRow + dy, 0);
  } else {
    item.w = Math.min(Math.max(drag.value.w + dx, 2), 12 - item.x);
    item.h = Math.min(Math.max(drag.value.h + dy, 1), 6);
  }
}

function cleanup(): void {
  window.removeEventListener("pointermove", onMove);
}

function onUp(): void {
  cleanup();
  window.removeEventListener("pointercancel", onCancel);
  drag.value = null;
  emit("save", props.layout);
}

function onCancel(): void {
  cleanup();
  drag.value = null;
}
</script>

<style scoped>
.bento {
  position: relative;
  background-image: linear-gradient(var(--wb-border) 1px, transparent 1px), linear-gradient(90deg, var(--wb-border) 1px, transparent 1px);
  background-size: 100% 112px, calc(100% / 12) 112px;
  border-radius: 8px;
}
.tile {
  position: absolute;
  border: 1.5px solid var(--wb-border);
  border-radius: var(--wb-radius-lg, 14px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: box-shadow 0.15s, transform 0.12s;
}
.tile:hover {
  box-shadow: var(--wb-shadow-2, 0 8px 24px rgba(20, 23, 40, 0.08));
  transform: translateY(-2px);
}
.tile.dragging {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.35);
  z-index: 5;
}
.tile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.45);
  cursor: grab;
  user-select: none;
}
.tile-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
  font-size: 14px;
  color: var(--wb-text);
}
.tile-title::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--wb-brand-500);
  opacity: 0.9;
}
.tile-tools .remove {
  color: var(--wb-text-3);
  cursor: pointer;
  padding: 0 4px;
  opacity: 0;
  transition: opacity var(--wb-duration, 160ms) var(--wb-ease, ease);
}
.tile:hover .tile-tools .remove {
  opacity: 1;
}
.tile-body {
  flex: 1;
  padding: 12px 14px;
  overflow: auto;
  font-size: 13px;
}
.resize {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 22px;
  height: 22px;
  cursor: nwse-resize;
  background:
    linear-gradient(135deg, transparent 46%, rgba(0, 0, 0, 0.18) 46%, rgba(0, 0, 0, 0.18) 52%, transparent 52%),
    linear-gradient(135deg, transparent 62%, rgba(0, 0, 0, 0.18) 62%, rgba(0, 0, 0, 0.18) 68%, transparent 68%);
  border-radius: 0 0 10px 0;
  z-index: 3;
}
.resize:hover {
  background:
    linear-gradient(135deg, transparent 46%, var(--wb-brand-500) 46%, var(--wb-brand-500) 52%, transparent 52%),
    linear-gradient(135deg, transparent 62%, var(--wb-brand-500) 62%, var(--wb-brand-500) 68%, transparent 68%);
}
</style>
