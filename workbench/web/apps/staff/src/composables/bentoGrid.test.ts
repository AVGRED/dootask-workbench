import test from "node:test";
import assert from "node:assert/strict";
import type { LayoutItem } from "@workbench/api";
import { buildColorIndex, colorOf, gridHeightOf, PALETTE, ROW, tileStyleOf } from "./bentoGrid.ts";

const layout: LayoutItem[] = [
  { id: "my-tasks", x: 0, y: 0, w: 6, h: 2 },
  { id: "daily-report", x: 6, y: 0, w: 3, h: 1 },
  { id: "capacity", x: 9, y: 0, w: 3, h: 1 },
  { id: "notifications", x: 6, y: 1, w: 3, h: 1 },
  { id: "summary", x: 9, y: 1, w: 3, h: 1 }
];

function touching(a: LayoutItem, b: LayoutItem): boolean {
  return a.x <= b.x + b.w && b.x <= a.x + a.w && a.y <= b.y + b.h && b.y <= a.y + a.h;
}

test("相邻板块颜色一定不同", () => {
  const index = buildColorIndex(layout);
  for (const a of layout) {
    for (const b of layout) {
      if (a.id === b.id || !touching(a, b)) continue;
      assert.notEqual(index[a.id], index[b.id], `${a.id} 与 ${b.id} 颜色相同`);
    }
  }
});

test("所有板块都能分到颜色", () => {
  const index = buildColorIndex(layout);
  for (const item of layout) {
    assert.equal(typeof index[item.id], "number");
    assert.ok(index[item.id] >= 0 && index[item.id] < PALETTE.length);
  }
});

test("颜色分配是确定性的（刷新不变）", () => {
  assert.deepEqual(buildColorIndex(layout), buildColorIndex([...layout].reverse()));
});

test("重叠（时间上叠放）的板块也视为相邻", () => {
  const overlap: LayoutItem[] = [
    { id: "my-tasks", x: 0, y: 0, w: 4, h: 2 },
    { id: "activity", x: 2, y: 0, w: 4, h: 2 }
  ];
  const index = buildColorIndex(overlap);
  assert.notEqual(index["my-tasks"], index.activity);
});

test("tileStyle 按 12 列与行高换算", () => {
  const style = tileStyleOf({ id: "my-tasks", x: 3, y: 2, w: 4, h: 2 }, {});
  assert.equal(style.left, "calc(25% + 4px)");
  assert.ok(style.width.startsWith("calc(33.333") && style.width.endsWith("% - 8px)"), style.width);
  assert.equal(style.top, `${2 * ROW + 4}px`);
  assert.equal(style.height, `${2 * ROW - 8}px`);
  assert.equal(style.background, colorOf({}, "my-tasks").bg);
});

test("网格高度按最底部板块计算", () => {
  assert.equal(gridHeightOf(layout), 2 * ROW + 8);
  assert.equal(gridHeightOf([]), 4 * ROW + 8);
});
