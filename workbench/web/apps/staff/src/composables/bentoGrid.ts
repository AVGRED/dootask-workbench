import type { LayoutItem } from "@workbench/api";

export const ROW = 112;
export const ROW_HEIGHT = ROW;
export const COL_PCT = 100 / 12;

export const PALETTE = [
  { bg: "#dbe8fb", border: "#a8c6ee" },
  { bg: "#dbf0e0", border: "#a6d1b3" },
  { bg: "#fbe6d0", border: "#eac095" },
  { bg: "#fbdee8", border: "#eeb2c6" },
  { bg: "#e3dff6", border: "#bdb3e2" },
  { bg: "#d6eeee", border: "#9fd0d4" },
  { bg: "#f7eec6", border: "#e0d18b" },
  { bg: "#e5e9f0", border: "#c0c9d7" }
];

function hashId(id: string): number {
  let hash = 0;
  for (let index = 0; index < id.length; index++) {
    hash = (hash * 31 + id.charCodeAt(index)) | 0;
  }
  return Math.abs(hash);
}

function isAdjacent(a: LayoutItem, b: LayoutItem): boolean {
  return a.x <= b.x + b.w && b.x <= a.x + a.w && a.y <= b.y + b.h && b.y <= a.y + a.h;
}

/** 按网格相邻关系给板块分配颜色，保证相邻板块不同色。 */
export function buildColorIndex(layout: LayoutItem[]): Record<string, number> {
  const result: Record<string, number> = {};
  const items = [...layout].sort((a, b) => a.y - b.y || a.x - b.x);
  for (const item of items) {
    const used = new Set<number>();
    for (const other of items) {
      if (other.id === item.id || result[other.id] === undefined) continue;
      if (isAdjacent(item, other)) used.add(result[other.id]);
    }
    let index = hashId(item.id) % PALETTE.length;
    let guard = 0;
    while (used.has(index) && guard < PALETTE.length) {
      index = (index + 1) % PALETTE.length;
      guard++;
    }
    result[item.id] = index;
  }
  return result;
}

export function colorOf(colorIndex: Record<string, number>, id: string): { bg: string; border: string } {
  return PALETTE[colorIndex[id] ?? 0] ?? PALETTE[0];
}

export function tileStyleOf(item: LayoutItem, colorIndex: Record<string, number>): Record<string, string> {
  const color = colorOf(colorIndex, item.id);
  return {
    background: color.bg,
    borderColor: color.border,
    left: `calc(${item.x * COL_PCT}% + 4px)`,
    width: `calc(${item.w * COL_PCT}% - 8px)`,
    top: `${item.y * ROW_HEIGHT + 4}px`,
    height: `${item.h * ROW_HEIGHT - 8}px`
  };
}

export function gridHeightOf(layout: LayoutItem[]): number {
  const rows = layout.length === 0 ? 4 : Math.max(...layout.map((item) => item.y + item.h));
  return rows * ROW_HEIGHT + 8;
}
