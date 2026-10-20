<template>
  <div class="starmap" :style="{ height: `${height}px` }">
    <svg class="canvas" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" preserveAspectRatio="xMidYMid meet">
      <defs>
        <filter id="soft-glow" x="-80%" y="-80%" width="260%" height="260%">
          <feGaussianBlur stdDeviation="7" />
        </filter>
      </defs>

      <g class="bg-stars">
        <g v-for="star in bgStars" :key="star.key">
          <circle :cx="star.x" :cy="star.y" :r="star.r" :opacity="star.opacity" class="bg-star" />
          <g v-if="star.cross" class="bg-cross" :opacity="star.opacity * 0.75">
            <line :x1="star.x - 4.5" :y1="star.y" :x2="star.x + 4.5" :y2="star.y" />
            <line :x1="star.x" :y1="star.y - 4.5" :x2="star.x" :y2="star.y + 4.5" />
          </g>
        </g>
      </g>

      <g class="edges">
        <g v-for="edge in edgesWithPos" :key="`${edge.from}-${edge.to}`">
          <path
            :d="edge.path"
            class="edge"
            :class="{ active: isEdgeActive(edge) }"
            :stroke-width="edgeWidth(edge.weight)"
            :stroke-opacity="edgeOpacity(edge)"
          />
          <text
            v-if="showEdgeLabels"
            class="edge-label"
            :x="edge.labelX"
            :y="edge.labelY"
            text-anchor="middle"
            :class="{ active: isEdgeActive(edge) }"
          >
            {{ edge.label }}
          </text>
        </g>
      </g>

      <g class="nodes">
        <g
          v-for="node in nodesWithPos"
          :key="node.userid"
          class="node"
          :class="{ self: node.self, dimmed: hovered !== null && !isRelated(node.userid), lit: hovered === node.userid }"
          :transform="`translate(${node.x}, ${node.y})`"
          @mouseenter="hovered = node.userid"
          @mouseleave="hovered = null"
        >
          <circle class="glow" :r="node.radius * 2.1" :style="{ fill: toneOf(node.status) }" filter="url(#soft-glow)" />
          <g class="spark" :style="{ stroke: toneOf(node.status) }">
            <line :x1="-(node.radius + 11)" y1="0" :x2="node.radius + 11" y2="0" />
            <line x1="0" :y1="-(node.radius + 11)" x2="0" :y2="node.radius + 11" />
          </g>
          <circle class="core" :r="node.radius" :style="{ stroke: toneOf(node.status) }" />
          <image
            v-if="node.avatar && node.radius >= 15"
            class="avatar"
            :href="`/api/v1/avatars/${node.avatar}`"
            :x="-node.radius * 0.58"
            :y="-node.radius * 0.58"
            :width="node.radius * 1.16"
            :height="node.radius * 1.16"
            :clip-path="`circle(${node.radius * 0.58}px at ${node.radius * 0.58}px ${node.radius * 0.58}px)`"
          />
          <text v-else-if="node.radius >= 13" class="initial" :dy="node.radius * 0.34" text-anchor="middle">
            {{ (node.nickname || "?").slice(0, 1) }}
          </text>
          <text class="label" :y="node.radius + 15" text-anchor="middle">
            {{ node.nickname || "未命名" }}<tspan v-if="node.self" class="self-tag">（我）</tspan>
          </text>
        </g>
      </g>
    </svg>

    <div class="hud">
      <span class="hud-item"><i class="dot" :style="{ background: toneOf('focus') }"></i>专注</span>
      <span class="hud-item"><i class="dot" :style="{ background: toneOf('meeting') }"></i>会议</span>
      <span class="hud-item"><i class="dot" :style="{ background: toneOf('away') }"></i>外出</span>
      <span class="hud-item"><i class="dot" :style="{ background: toneOf('leave') }"></i>请假</span>
      <span class="hud-item"><i class="dot" :style="{ background: toneOf('idle') }"></i>空闲</span>
      <span class="hud-sep"></span>
      <span class="hud-text">连线 = 共同项目</span>
    </div>

    <div v-if="hoveredNode" class="tooltip" :style="tooltipStyle">
      <div class="tt-name">
        {{ hoveredNode.nickname || "未命名" }} <span class="tt-uid">#{{ hoveredNode.userid }}</span>
      </div>
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

const props = withDefaults(defineProps<{ data: StarMapView; height?: number }>(), { height: 480 });

const WIDTH = 900;
const HEIGHT = 560;

const TONE_COLOR: Record<string, string> = {
  focus: "var(--wb-brand-600)",
  meeting: "var(--wb-warning)",
  away: "var(--wb-success)",
  leave: "var(--wb-danger)",
  idle: "var(--wb-text-3)"
};

const STATUS: Record<string, string> = {
  focus: "专注",
  idle: "空闲",
  meeting: "会议",
  away: "外出",
  leave: "请假"
};

function toneOf(status: string): string {
  return TONE_COLOR[status] ?? TONE_COLOR.idle;
}

function statusText(status: string): string {
  return STATUS[status] ?? status;
}

function seeded(seed: number): () => number {
  let state = seed % 2147483647;
  if (state <= 0) state += 2147483646;
  return () => {
    state = (state * 16807) % 2147483647;
    return (state - 1) / 2147483646;
  };
}

const bgStars = computed(() => {
  const rand = seeded(4271);
  const stars: { key: number; x: number; y: number; r: number; opacity: number; cross: boolean }[] = [];
  for (let i = 0; i < 90; i++) {
    stars.push({
      key: i,
      x: rand() * WIDTH,
      y: rand() * HEIGHT,
      r: 0.8 + rand() * 1.1,
      opacity: 0.28 + rand() * 0.42,
      cross: i % 9 === 4
    });
  }
  return stars;
});

/** 简单力导向布局：斥力 + 弹簧 + 向心，确定性初值。 */
const nodesWithPos = computed(() => {
  const nodes = props.data.nodes;
  const links = props.data.edges;
  if (nodes.length === 0) return [];

  const cx = WIDTH / 2;
  const cy = HEIGHT / 2;
  const rand = seeded(8821);

  const positions = new Map<number, { x: number; y: number; vx: number; vy: number }>();
  nodes.forEach((node, index) => {
    const angle = (index / nodes.length) * Math.PI * 2 + rand() * 0.4;
    const radius = 120 + rand() * 80;
    positions.set(node.userid, {
      x: cx + Math.cos(angle) * radius,
      y: cy + Math.sin(angle) * radius,
      vx: 0,
      vy: 0
    });
  });

  const REPULSION = 26000;
  const SPRING = 0.008;
  const SPRING_LENGTH = 220;
  const GRAVITY = 0.0015;
  const DAMPING = 0.9;

  for (let step = 0; step < 420; step++) {
    const list = [...positions.entries()];
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        const a = list[i][1];
        const b = list[j][1];
        let dx = a.x - b.x;
        let dy = a.y - b.y;
        let dist = Math.hypot(dx, dy) || 0.01;
        if (dist < 1) dist = 1;
        const force = REPULSION / (dist * dist);
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        a.vx += fx;
        a.vy += fy;
        b.vx -= fx;
        b.vy -= fy;
      }
    }

    for (const link of links) {
      const a = positions.get(link.from);
      const b = positions.get(link.to);
      if (!a || !b) continue;
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const dist = Math.hypot(dx, dy) || 0.01;
      const diff = (dist - SPRING_LENGTH) * SPRING;
      const fx = (dx / dist) * diff;
      const fy = (dy / dist) * diff;
      a.vx += fx;
      a.vy += fy;
      b.vx -= fx;
      b.vy -= fy;
    }

    for (const node of positions.values()) {
      node.vx += (cx - node.x) * GRAVITY;
      node.vy += (cy - node.y) * GRAVITY;
      node.vx *= DAMPING;
      node.vy *= DAMPING;
      node.x += node.vx;
      node.y += node.vy;
      node.x = Math.min(Math.max(node.x, 90), WIDTH - 90);
      node.y = Math.min(Math.max(node.y, 70), HEIGHT - 70);
    }
  }

  return nodes.map((node: StarNode) => {
    const pos = positions.get(node.userid)!;
    return {
      ...node,
      x: pos.x,
      y: pos.y,
      radius: 17 + Math.min(node.projectCount, 6) * 2 + (node.self ? 7 : 0)
    };
  });
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
      const mx = (from.x + to.x) / 2;
      const my = (from.y + to.y) / 2;
      const nx = -(to.y - from.y);
      const ny = to.x - from.x;
      const norm = Math.hypot(nx, ny) || 1;
      const bow = 10;
      const cx = mx + (nx / norm) * bow;
      const cy = my + (ny / norm) * bow;
      const labels = (edge.projects ?? []).filter(Boolean);
      const text = labels.length > 0 ? labels.join(" · ") : `${edge.weight} 个项目`;
      return {
        ...edge,
        path: `M ${from.x} ${from.y} Q ${cx} ${cy} ${to.x} ${to.y}`,
        labelX: from.x * 0.25 + cx * 0.5 + to.x * 0.25,
        labelY: from.y * 0.25 + cy * 0.5 + to.y * 0.25,
        label: text.length > 18 ? `${text.slice(0, 17)}…` : text
      };
    })
    .filter((edge): edge is NonNullable<typeof edge> => edge !== null)
);

const showEdgeLabels = computed(() => props.data.edges.length <= 14);
const hovered = ref<number | null>(null);
const hoveredNode = computed(() => props.data.nodes.find((node) => node.userid === hovered.value) ?? null);

function edgeWidth(weight: number): number {
  return Math.min(1 + weight * 0.8, 3);
}

function edgeOpacity(edge: { weight: number }): number {
  return hovered.value === null ? Math.min(0.28 + edge.weight * 0.12, 0.55) : 0.15;
}

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
  return {
    left: `${Math.min(Math.max((pos.x / WIDTH) * 100, 12), 82)}%`,
    top: `${Math.max((pos.y / HEIGHT) * 100 - 6, 8)}%`
  };
});
</script>

<style scoped>
.starmap {
  position: relative;
  border-radius: var(--wb-radius-lg);
  border: 1px solid var(--wb-border);
  background:
    radial-gradient(720px 380px at 15% 10%, color-mix(in srgb, var(--wb-brand-500) 14%, transparent), transparent 62%),
    radial-gradient(640px 340px at 86% 84%, color-mix(in srgb, var(--wb-brand-400) 12%, transparent), transparent 62%),
    radial-gradient(520px 280px at 50% 50%, color-mix(in srgb, var(--wb-brand-300) 10%, transparent), transparent 70%),
    linear-gradient(170deg, color-mix(in srgb, var(--wb-brand-50) 70%, var(--wb-surface)), var(--wb-surface));
  overflow: hidden;
}
.canvas {
  position: relative;
  width: 100%;
  height: 100%;
  z-index: 1;
}
.bg-star {
  fill: var(--wb-brand-300);
}
.bg-cross line {
  stroke: var(--wb-brand-300);
  stroke-width: 0.9;
}
.edge {
  fill: none;
  stroke: var(--wb-brand-400);
  transition: stroke-opacity 160ms ease, stroke 160ms ease;
}
.edge.active {
  stroke: var(--wb-brand-600);
}
.edge-label {
  font-size: 10.5px;
  fill: var(--wb-text-2);
  paint-order: stroke;
  stroke: var(--wb-surface);
  stroke-width: 3px;
  pointer-events: none;
  transition: fill 160ms ease;
}
.edge-label.active {
  fill: var(--wb-brand-700);
  font-weight: 600;
}
.node {
  cursor: pointer;
  transition: opacity 180ms ease;
}
.node.dimmed {
  opacity: 0.3;
}
.node .glow {
  opacity: 0.3;
  transition: opacity 180ms ease;
}
.node:hover .glow,
.node.lit .glow {
  opacity: 0.55;
}
.spark line {
  stroke-width: 1.1;
  opacity: 0.45;
  stroke-linecap: round;
}
.node:hover .spark line,
.node.lit .spark line {
  opacity: 0.8;
}
.node .core {
  fill: var(--wb-surface);
  stroke-width: 2;
}
.node.self .core {
  fill: color-mix(in srgb, var(--wb-brand-600) 12%, var(--wb-surface));
  stroke-width: 2.5;
}
.node .initial {
  font-size: 14px;
  font-weight: 700;
  fill: var(--wb-text);
}
.node .label {
  font-size: 12.5px;
  fill: var(--wb-text-2);
}
.self-tag {
  fill: var(--wb-brand-600);
}
.avatar {
  pointer-events: none;
}
.hud {
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
  backdrop-filter: blur(8px);
  font-size: 11.5px;
  color: var(--wb-text-3);
  z-index: 2;
}
.hud-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.hud-sep {
  width: 1px;
  height: 11px;
  background: var(--wb-border);
}
.hud-text {
  color: var(--wb-text-3);
}
.tooltip {
  position: absolute;
  transform: translate(-50%, -110%);
  min-width: 172px;
  padding: 10px 12px;
  border-radius: var(--wb-radius);
  background: var(--wb-surface);
  border: 1px solid var(--wb-border);
  box-shadow: var(--wb-shadow-2);
  font-size: 12px;
  color: var(--wb-text-2);
  pointer-events: none;
  z-index: 3;
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
  z-index: 2;
}
</style>
