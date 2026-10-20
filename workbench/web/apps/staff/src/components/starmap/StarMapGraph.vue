<template>
  <div class="starmap" :style="{ height: `${height}px` }">
    <div class="starfield"></div>

    <svg class="canvas" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" preserveAspectRatio="xMidYMid meet">
      <g class="edges">
        <line
          v-for="edge in edgesWithPos"
          :key="`${edge.from}-${edge.to}`"
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          :stroke-width="edgeWidth(edge.weight)"
          :class="{ active: isEdgeActive(edge) }"
        />
      </g>

      <g class="nodes">
        <g
          v-for="node in nodesWithPos"
          :key="node.userid"
          class="node"
          :class="{ self: node.self, dimmed: hovered !== null && !isRelated(node.userid) }"
          :transform="`translate(${node.x}, ${node.y})`"
          @mouseenter="hovered = node.userid"
          @mouseleave="hovered = null"
        >
          <circle class="halo" :r="node.radius + 8" :class="`s-${node.status}`" />
          <circle class="body" :r="node.radius" :class="`s-${node.status}`" />
          <image
            v-if="node.avatar"
            class="avatar"
            :href="`/api/v1/avatars/${node.avatar}`"
            :x="-node.radius"
            :y="-node.radius"
            :width="node.radius * 2"
            :height="node.radius * 2"
            :clip-path="`circle(${node.radius}px at ${node.radius}px ${node.radius}px)`"
          />
          <text v-else class="initial" :dy="node.radius * 0.36" text-anchor="middle">
            {{ (node.nickname || "?").slice(0, 1) }}
          </text>
          <text class="label" :y="node.radius + 15" text-anchor="middle">
            {{ node.nickname || "未命名" }}<tspan v-if="node.self" class="self-tag">（我）</tspan>
          </text>
        </g>
      </g>
    </svg>

    <div class="legend">
      <span class="legend-item"><i class="dot brand"></i>专注</span>
      <span class="legend-item"><i class="dot warn"></i>会议</span>
      <span class="legend-item"><i class="dot ok"></i>外出</span>
      <span class="legend-item"><i class="dot danger"></i>请假</span>
      <span class="legend-item"><i class="dot idle"></i>空闲</span>
      <span class="legend-sep"></span>
      <span class="legend-text">连线＝共同项目，越粗合作越多</span>
    </div>

    <div v-if="hoveredNode" class="tooltip" :style="tooltipStyle">
      <div class="tt-name">{{ hoveredNode.nickname || "未命名" }} <span class="tt-uid">#{{ hoveredNode.userid }}</span></div>
      <div class="tt-row">{{ statusText(hoveredNode.status) }} · {{ hoveredNode.role === "admin" ? "管理员" : "员工" }}</div>
      <div class="tt-row">参与项目 {{ hoveredNode.projectCount }} 个</div>
      <div v-if="hoveredNode.note" class="tt-note">“{{ hoveredNode.note }}”</div>
    </div>

    <div v-if="data.nodes.length === 0" class="empty">还没有协作数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { StarMapView, StarNode } from "@workbench/api";

const props = withDefaults(defineProps<{ data: StarMapView; height?: number }>(), { height: 520 });

const WIDTH = 900;
const HEIGHT = 560;
const height = HEIGHT;

const hovered = ref<number | null>(null);

const STATUS: Record<string, string> = {
  focus: "专注",
  idle: "空闲",
  meeting: "会议",
  away: "外出",
  leave: "请假"
};

function statusText(status: string): string {
  return STATUS[status] ?? status;
}

const nodesWithPos = computed(() => {
  const nodes = props.data.nodes;
  const centerId = props.data.centerUserid;
  const center = nodes.find((node) => node.userid === centerId) ?? nodes[0];
  const others = nodes.filter((node) => node !== center);

  const cx = WIDTH / 2;
  const cy = HEIGHT / 2 - 10;
  const positioned: (StarNode & { x: number; y: number; radius: number })[] = [];

  // 人少时把所有人放在一个环上（避免连线穿过中心节点）；人多时中心放"我"
  const ringOnly = nodes.length <= 3;

  if (ringOnly) {
    const radius = Math.min(WIDTH, HEIGHT) * 0.26;
    nodes.forEach((node, index) => {
      const angle = -Math.PI / 2 + (index / nodes.length) * Math.PI * 2;
      positioned.push({
        ...node,
        x: cx + Math.cos(angle) * radius,
        y: cy + Math.sin(angle) * radius,
        radius: 18 + Math.min(node.projectCount, 6) * 2
      });
    });
    return positioned;
  }

  const radius = Math.min(WIDTH, HEIGHT) * 0.33;

  if (center) {
    positioned.push({
      ...center,
      x: cx,
      y: cy,
      radius: 34 + Math.min(center.projectCount, 6) * 2
    });
  }

  others.forEach((node, index) => {
    const angle = -Math.PI / 2 + (index / Math.max(others.length, 1)) * Math.PI * 2;
    positioned.push({
      ...node,
      x: cx + Math.cos(angle) * radius,
      y: cy + Math.sin(angle) * radius,
      radius: 18 + Math.min(node.projectCount, 6) * 2
    });
  });

  return positioned;
});

const positionOf = computed(() => {
  const map = new Map<number, { x: number; y: number }>();
  nodesWithPos.value.forEach((node) => map.set(node.userid, { x: node.x, y: node.y }));
  return map;
});

const edgesWithPos = computed(() =>
  props.data.edges
    .map((edge) => {
      const from = positionOf.value.get(edge.from);
      const to = positionOf.value.get(edge.to);
      if (!from || !to) return null;
      return { ...edge, x1: from.x, y1: from.y, x2: to.x, y2: to.y };
    })
    .filter((edge): edge is NonNullable<typeof edge> => edge !== null)
);

function edgeWidth(weight: number): number {
  return Math.min(1 + weight * 0.8, 4);
}

const hoveredNode = computed(() => props.data.nodes.find((node) => node.userid === hovered.value) ?? null);

function isRelated(userid: number): boolean {
  if (hovered.value === null) return true;
  if (userid === hovered.value) return true;
  return props.data.edges.some(
    (edge) =>
      (edge.from === hovered.value && edge.to === userid) || (edge.to === hovered.value && edge.from === userid)
  );
}

function isEdgeActive(edge: { from: number; to: number }): boolean {
  if (hovered.value === null) return false;
  return edge.from === hovered.value || edge.to === hovered.value;
}

const tooltipStyle = computed(() => {
  const node = hoveredNode.value;
  if (!node) return {};
  const pos = positionOf.value.get(node.userid);
  if (!pos) return {};
  const left = (pos.x / WIDTH) * 100;
  const top = (pos.y / HEIGHT) * 100;
  return { left: `${Math.min(Math.max(left, 12), 82)}%`, top: `${Math.max(top - 4, 8)}%` };
});
</script>

<style scoped>
.starmap {
  position: relative;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background:
    radial-gradient(circle at 20% 20%, color-mix(in srgb, var(--wb-brand-600) 10%, transparent), transparent 45%),
    radial-gradient(circle at 80% 75%, color-mix(in srgb, var(--wb-brand-400) 12%, transparent), transparent 50%),
    var(--wb-surface);
  overflow: hidden;
}
.starfield {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--wb-border-strong) 1px, transparent 1px);
  background-size: 26px 26px;
  opacity: 0.35;
  pointer-events: none;
}
.canvas {
  position: relative;
  width: 100%;
  height: 100%;
}
.edges line {
  stroke: var(--wb-brand-500);
  opacity: 0.18;
  transition: opacity var(--wb-duration) var(--wb-ease);
}
.edges line.active {
  opacity: 0.85;
}
.node {
  cursor: pointer;
  transition: opacity var(--wb-duration) var(--wb-ease);
}
.node.dimmed {
  opacity: 0.35;
}
.node .halo {
  fill: transparent;
  stroke-width: 6;
  opacity: 0.16;
  stroke: var(--wb-text-3);
}
.node .halo.s-focus { stroke: var(--wb-brand-600); }
.node .halo.s-meeting { stroke: var(--wb-warning); }
.node .halo.s-away { stroke: var(--wb-success); }
.node .halo.s-leave { stroke: var(--wb-danger); }
.node .body {
  fill: var(--wb-surface);
  stroke-width: 2.5;
  stroke: var(--wb-border-strong);
}
.node .body.s-focus { stroke: var(--wb-brand-600); }
.node .body.s-meeting { stroke: var(--wb-warning); }
.node .body.s-away { stroke: var(--wb-success); }
.node .body.s-leave { stroke: var(--wb-danger); }
.node.self .body {
  fill: color-mix(in srgb, var(--wb-brand-600) 10%, var(--wb-surface));
  stroke-width: 3.5;
}
.node .initial {
  font-size: 15px;
  font-weight: 700;
  fill: var(--wb-brand-700);
}
.node .label {
  font-size: 11.5px;
  fill: var(--wb-text-2);
}
.self-tag {
  fill: var(--wb-brand-600);
}
.legend {
  position: absolute;
  left: 14px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--wb-surface) 88%, transparent);
  border: 1px solid var(--wb-border);
  backdrop-filter: blur(6px);
  font-size: 11.5px;
  color: var(--wb-text-3);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot.brand { background: var(--wb-brand-600); }
.dot.warn { background: var(--wb-warning); }
.dot.ok { background: var(--wb-success); }
.dot.danger { background: var(--wb-danger); }
.dot.idle { background: var(--wb-text-3); }
.legend-sep {
  width: 1px;
  height: 12px;
  background: var(--wb-border);
}
.tooltip {
  position: absolute;
  transform: translate(-50%, -110%);
  min-width: 168px;
  padding: 10px 12px;
  border-radius: var(--wb-radius);
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  box-shadow: var(--wb-shadow-2);
  font-size: 12px;
  color: var(--wb-text-2);
  pointer-events: none;
  z-index: 2;
}
.tt-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-text);
  margin-bottom: 4px;
}
.tt-uid {
  font-weight: 400;
  font-size: 11px;
  color: var(--wb-text-3);
}
.tt-row {
  line-height: 1.6;
}
.tt-note {
  margin-top: 4px;
  color: var(--wb-text-3);
}
.empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wb-text-3);
  font-size: 13px;
}
</style>
