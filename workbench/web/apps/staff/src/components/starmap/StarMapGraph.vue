<template>
  <div class="starmap" :style="{ height: `${height}px` }">
    <svg class="canvas" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" preserveAspectRatio="xMidYMid meet">
      <defs>
        <radialGradient v-for="tone in TONES" :id="`star-${tone}`" :key="tone">
          <stop offset="0%" :stop-color="TONE_COLOR[tone]" stop-opacity="0.95" />
          <stop offset="45%" :stop-color="TONE_COLOR[tone]" stop-opacity="0.35" />
          <stop offset="100%" :stop-color="TONE_COLOR[tone]" stop-opacity="0" />
        </radialGradient>
        <filter id="soft-glow" x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation="6" />
        </filter>
      </defs>

      <g class="bg-stars">
        <circle
          v-for="star in bgStars"
          :key="star.key"
          :cx="star.x"
          :cy="star.y"
          :r="star.r"
          :opacity="star.opacity"
          class="bg-star"
          :style="{ animationDelay: `${star.delay}s`, animationDuration: `${star.duration}s` }"
        />
      </g>

      <g class="edges">
        <path
          v-for="edge in edgesWithPos"
          :key="`${edge.from}-${edge.to}`"
          :d="edge.path"
          class="edge"
          :class="{ active: isEdgeActive(edge) }"
          :stroke-width="edgeWidth(edge.weight)"
          :stroke-opacity="edgeOpacity(edge)"
        />
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
          <circle class="glow" :r="node.radius * 2.6" :fill="`url(#star-${node.status})`" />
          <circle class="core" :r="node.radius" :style="{ fill: TONE_COLOR[node.status] ?? TONE_COLOR.idle }" />
          <image
            v-if="node.avatar && node.radius >= 14"
            class="avatar"
            :href="`/api/v1/avatars/${node.avatar}`"
            :x="-node.radius * 0.62"
            :y="-node.radius * 0.62"
            :width="node.radius * 1.24"
            :height="node.radius * 1.24"
            :clip-path="`circle(${node.radius * 0.62}px at ${node.radius * 0.62}px ${node.radius * 0.62}px)`"
          />
          <text v-else-if="node.radius >= 14" class="initial" :dy="node.radius * 0.34" text-anchor="middle">
            {{ (node.nickname || "?").slice(0, 1) }}
          </text>
          <text class="label" :y="node.radius + 16" text-anchor="middle">
            {{ node.nickname || "未命名" }}<tspan v-if="node.self" class="self-tag">（我）</tspan>
          </text>
        </g>
      </g>
    </svg>

    <div class="hud">
      <span class="hud-item"><i class="dot" style="background: #818cf8"></i>专注</span>
      <span class="hud-item"><i class="dot" style="background: #f59e0b"></i>会议</span>
      <span class="hud-item"><i class="dot" style="background: #22c55e"></i>外出</span>
      <span class="hud-item"><i class="dot" style="background: #ef4444"></i>请假</span>
      <span class="hud-item"><i class="dot" style="background: #94a3b8"></i>空闲</span>
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

const props = withDefaults(defineProps<{ data: StarMapView; height?: number }>(), { height: 520 });

const WIDTH = 900;
const HEIGHT = 560;

const TONES = ["focus", "idle", "meeting", "away", "leave"] as const;
const TONE_COLOR: Record<string, string> = {
  focus: "#818cf8",
  meeting: "#f59e0b",
  away: "#22c55e",
  leave: "#ef4444",
  idle: "#94a3b8"
};

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
  const stars: { key: number; x: number; y: number; r: number; opacity: number; delay: number; duration: number }[] = [];
  for (let i = 0; i < 140; i++) {
    stars.push({
      key: i,
      x: rand() * WIDTH,
      y: rand() * HEIGHT,
      r: 0.6 + rand() * 1.5,
      opacity: 0.2 + rand() * 0.6,
      delay: rand() * 4,
      duration: 3 + rand() * 4
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
  const SPRING_LENGTH = 240;
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
      radius: 14 + Math.min(node.projectCount, 6) * 2 + (node.self ? 7 : 0)
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
      const bow = 12;
      const cx = mx + (nx / norm) * bow;
      const cy = my + (ny / norm) * bow;
      return { ...edge, path: `M ${from.x} ${from.y} Q ${cx} ${cy} ${to.x} ${to.y}` };
    })
    .filter((edge): edge is NonNullable<typeof edge> => edge !== null)
);

const hovered = ref<number | null>(null);
const hoveredNode = computed(() => props.data.nodes.find((node) => node.userid === hovered.value) ?? null);

function edgeWidth(weight: number): number {
  return Math.min(1 + weight * 0.9, 3.5);
}

function edgeOpacity(edge: { weight: number }): number {
  return hovered.value === null ? Math.min(0.25 + edge.weight * 0.12, 0.6) : 0.18;
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
  border: 1px solid rgba(129, 140, 248, 0.22);
  background:
    radial-gradient(ellipse 60% 50% at 25% 20%, rgba(99, 102, 241, 0.3), transparent 60%),
    radial-gradient(ellipse 50% 45% at 78% 78%, rgba(56, 189, 248, 0.18), transparent 62%),
    radial-gradient(ellipse 40% 40% at 60% 35%, rgba(168, 85, 247, 0.14), transparent 65%),
    linear-gradient(160deg, #070b1c 0%, #0a1026 45%, #05070f 100%);
  overflow: hidden;
}
.canvas {
  position: relative;
  width: 100%;
  height: 100%;
  z-index: 1;
}
.bg-star {
  fill: #dbeafe;
  animation: twinkle 5s ease-in-out infinite;
}
@keyframes twinkle {
  0%, 100% { opacity: 0.25; }
  50% { opacity: 0.9; }
}
.edge {
  fill: none;
  stroke: #a5b4fc;
  filter: url(#soft-glow);
  transition: stroke-opacity 160ms ease, stroke 160ms ease;
}
.edge.active {
  stroke: #e0e7ff;
}
.node {
  cursor: pointer;
  transition: opacity 180ms ease;
}
.node.dimmed {
  opacity: 0.28;
}
.node .glow {
  opacity: 0.55;
  transition: opacity 180ms ease;
}
.node:hover .glow,
.node.lit .glow {
  opacity: 0.95;
}
.node .core {
  stroke: rgba(255, 255, 255, 0.62);
  stroke-width: 1;
}
.node.self .core {
  stroke: #fff;
  stroke-width: 2;
}
.node .initial {
  font-size: 12px;
  font-weight: 700;
  fill: #0b1024;
}
.node .label {
  font-size: 11.5px;
  fill: #cbd5f5;
  paint-order: stroke;
  stroke: rgba(5, 8, 20, 0.75);
  stroke-width: 3px;
}
.self-tag {
  fill: #c7d2fe;
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
  background: rgba(10, 16, 38, 0.6);
  border: 1px solid rgba(165, 180, 252, 0.22);
  backdrop-filter: blur(8px);
  font-size: 11.5px;
  color: #cbd5f5;
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
  background: rgba(165, 180, 252, 0.3);
}
.hud-text {
  color: #94a3c8;
}
.tooltip {
  position: absolute;
  transform: translate(-50%, -110%);
  min-width: 172px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(12, 18, 40, 0.92);
  border: 1px solid rgba(165, 180, 252, 0.3);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.55);
  font-size: 12px;
  color: #cbd5f5;
  pointer-events: none;
  z-index: 3;
  backdrop-filter: blur(8px);
}
.tt-name {
  font-size: 13px;
  font-weight: 600;
  color: #f8fafc;
  margin-bottom: 4px;
}
.tt-uid {
  font-weight: 400;
  font-size: 11px;
  color: #94a3c8;
}
.tt-row {
  line-height: 1.6;
}
.tt-note {
  margin-top: 4px;
  color: #94a3c8;
}
.empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3c8;
  font-size: 13px;
  z-index: 2;
}
@media (prefers-reduced-motion: reduce) {
  .bg-star { animation: none; opacity: 0.6; }
}
</style>
