# 俄罗斯方块（tetris）

## 1. 游戏类型与视角

| 项目 | 值 |
|------|---|
| 类型 | 单人 |
| 视角约定 | 无方向 |
| 屏幕方向 | 竖屏 |

## 2. 数据模型

```kotlin
val COLS = 10
val ROWS = 20
val board = IntArray(COLS * ROWS)    // 0=空，1~7=方块颜色索引
var curShape: Int = 0                 // 当前方块类型索引（0~6）
var curCells = mutableListOf<Pair<Int,Int>>()  // 当前方块相对坐标
var curX: Int = 3                     // 当前方块左上角列
var curY: Int = 0                     // 当前方块左上角行
var nextShape: Int = 0                // 下一个方块类型
var score: Int = 0
var lines: Int = 0                    // 已消行数
var fallAcc: Float = 0f               // 下落累加器
var softDrop: Boolean = false         // 是否加速下落（按住 ▼）
```

**七种方块**（I/O/T/S/Z/J/L）：每种用 4 个 `(col, row)` 相对坐标表示。

## 3. 初始化逻辑

- 棋盘清零，`score=0`，`lines=0`
- 随机生成 `nextShape`
- 调用 `spawnPiece()` 生成当前方块

## 4. 输入处理

- 屏幕下方 4 个按钮：◀ 左移、▶ 右移、↻ 旋转、▼ 加速下落
- `ACTION_DOWN` 检测按钮区域，`ACTION_UP` 取消 softDrop

## 5. 核心更新循环

```
update(dt):
  fallAcc += dt * (softDrop ? 18 : 1/fallInterval())
  while fallAcc >= 1:
    fallAcc -= 1
    tryDrop()  // 若不能下落则 lockPiece()

fallInterval() = max(0.07, 0.9 - lines * 0.015)  // 每消一行加速

rotate(): 将 curCells 做 (col, row) → (3-row, col) 变换，检查 fits 后生效
moveX(dx): 检查 fits 后 curX += dx
lockPiece(): 将 curCells 写入 board，调用 clearLines()，再 spawnPiece()
clearLines(): 从底向上扫描满行消除，上方行下移
```

## 6. 胜负判定

- **失败**：`spawnPiece()` 时新方块初始位置 `!fits` → 方块堆到顶 → `gameOver(score, false, ...)`
- **无胜利条件**（无尽模式）

## 7. 边界与特殊规则

1. **旋转踢墙**：旋转后若 `fits` 检查失败则不旋转（不做踢墙补偿）
2. **加速梯度**：`fallInterval = max(0.07, 0.9 - lines * 0.015)`——每消一行下落间隔缩短
3. **softDrop 速度**：18 倍普通速度
4. **消行计分**：1 行 100，2 行 300，3 行 500，4 行 800
5. **方块预览**：右侧显示下一个方块（缩小 0.55 倍绘制）
6. **消行显示**：右侧显示已消行数

## 8. 渲染要求

- 棋盘 10×20 格子，每格圆角矩形
- 当前方块和已锁定方块用 7 种颜色区分
- 下方 4 个操作按钮
- 右侧显示"下一个"预览和消行数
