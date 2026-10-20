# 星图 · 品牌与 Logo

## 概念

「星图」= 星星 + 图（连接关系）。Logo 把两件事画在一起：

- **一颗四角星**：代表"一个人/一件事"——星图里每个员工就是一颗星
- **两条细连线连向两个小圆点**：代表"共同项目/协作关系"——这也是产品里星图页的视觉原型
- 背景是靛蓝渐变圆角方块（`#4338ca → #818cf8`），与产品主色 `--wb-brand-600` 同族

一句话描述：**一颗星连着两颗星，就是一张星图。**

## 文件

| 文件 | 用途 |
|---|---|
| `logo-mark.svg` | 主图标（渐变底 + 白星），用于 App 图标、登录页、头像位 |
| `logo-mark-compact.svg` | 简化版（星星 + 一个节点），用于 32px 以下 favicon |
| `logo-mark-mono.svg` | 单色版（`currentColor`），用于印刷、水印、深色背景反白 |
| `logo-horizontal.svg` | 横版组合（图标 + `星图` + `STARMAP`） |
| `png/icon-512.png`、`icon-192.png`、`icon-64.png` | 桌面/移动端图标 |
| `png/icon-32.png`、`icon-64-compact.png` | 小尺寸 favicon（用简化版） |
| `png/icon-512-light.png` | 浅色背景预览 |
| `png/mark-mono.png`、`png/horizontal.png` | 单色与横版预览 |

重新导出：

```powershell
cd workbench/web/apps/e2e
node render-logo.mjs   # 输出到 workbench/brand/png
```

## 颜色

| 用途 | 色值 |
|---|---|
| 渐变起点 | `#4338ca`（brand-700） |
| 渐变中点 | `#5b52ea` |
| 渐变终点 | `#818cf8`（brand-400） |
| 图标内图形 | `#ffffff` |
| 文字主色 | `#1e2233`（`--wb-text`） |
| 文字辅色 | `#8b91a4`（`--wb-text-3`，`STARMAP` 字母间距 4.2） |

## 使用建议

- **最小尺寸**：主图标 ≥ 24px；更小请用 compact 版
- **留白**：图标四周至少留 1/4 图标宽度的空白
- **不要**：给图标加描边、换非品牌色渐变、拉长比例、在图标上叠加文字
- **深色背景**：用 `logo-mark.svg`（自带渐变底）或 `logo-mark-mono.svg` 反白

## 已接入的位置

| 位置 | 文件 |
|---|---|
| 员工端 / 管理端 favicon | `apps/{staff,admin}/public/favicon.svg`、`favicon-32.png`、`icon-192/512.png` |
| 移动端 PWA 图标 | `apps/mobile/public/icon.svg`、`icon-192.png`、`icon-512.png`；`manifest.webmanifest` 的 `icons` 与 `theme_color` |
| 移动端主题色 | `#409eff` → `#4f46e5` |

> 应用内顶栏与登录页的品牌标记目前还是 CSS 星形（`clip-path`）。若要统一成 SVG 图标，可把 `AppHeader.vue` 的 `.brand-dot` 和登录页的 `.brand-dot` 换成 `<img src="/favicon.svg">`——需要时再做。
