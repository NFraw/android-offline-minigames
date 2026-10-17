# 扫雷（mines）

## 1. 游戏类型与视角

| 项目 | 值 |
|------|---|
| 类型 | 单人 |
| 视角约定 | 无方向 |
| 屏幕方向 | 竖屏 |

## 2. 数据模型

```kotlin
val COLS = 9
val ROWS = 12
val MINES = 15
val MINE = -1

val grid = IntArray(COLS * ROWS)        // -1=雷，0~8=周围雷数
val revealed = BooleanArray(COLS * ROWS) // 已翻开
val flagged = BooleanArray(COLS * ROWS)  // 已插旗
var firstTap: Boolean = true             // 首次点击（首次点击后才布雷）
var minesPlaced: Boolean = false
var over: Boolean = false
var time: Float = 0f                     // 用时（秒）
var revealedCount: Int = 0
```

## 3. 初始化逻辑

- 全部清零，`firstTap=true`，`over=false`，`time=0`
- 雷区延迟到首次点击后生成

## 4. 输入处理

- **点击**（`ACTION_UP` 且非长按）：`revealAt(x, y)` 翻开格子
- **长按**：`toggleFlag(x, y)` 切换插旗
- 使用 `GestureDetector` 识别长按

## 5. 核心更新循环

```
update(dt):
  若 !over → time += dt  // 计时
```

纯事件驱动，update 只计时。

```
revealAt(x, y):
  若首次点击 → placeMinesAround(safeIdx)  // 布雷（避开首击格及 8 邻域）
  reveal(idx)

reveal(idx):
  若雷 → gameOver("踩到地雷了！")
  revealedCount++
  若 grid[idx] == 0 → 递归翻开 8 邻域（flood fill）
  若 revealedCount == COLS*ROWS - MINES → 胜利
```

## 6. 胜负判定

- **失败**：翻开雷格 → `gameOver(0, false, "踩到地雷了！")`
- **胜利**：翻开所有非雷格 → 得分 = `max(0, 500 - time*2)`，`gameOver(score, true, ...)`

## 7. 边界与特殊规则

1. **首击安全**：首次点击的格子及其 8 邻域不会布雷
2. **首击展开**：重试最多 30 次，确保首击格为 0（可 flood fill 展开一片）
3. **flood fill**：翻开 0 格时递归翻开所有相邻非雷格
4. **插旗**：长按切换，不影响翻开逻辑（插旗格点击无效）
5. **计时**：从游戏开始持续计时，胜利得分与用时相关
6. **数字颜色**：1~8 各有不同颜色（蓝、绿、红、紫等）
7. **剩余雷数显示**：顶部显示"剩余地雷 N"（总雷数 - 插旗数）

## 8. 渲染要求

- 9×12 格子，未翻开深色，翻开浅色
- 雷格显示"💣"，插旗格显示"🚩"
- 数字用对应颜色显示
- 顶部显示剩余雷数和用时
