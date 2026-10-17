# 别踩白块（piano）

## 1. 游戏类型与视角

| 项目 | 值 |
|------|---|
| 类型 | 单人 |
| 视角约定 | 无方向 |
| 屏幕方向 | 竖屏 |

## 2. 数据模型

```kotlin
data class Row(val col: Int, var y: Float, var hit: Boolean)
// col: 黑块所在列（0~3），y: 行顶 Y 坐标，hit: 是否已点击

val COLS = 4
val MAX_SPEED = 3.0f          // 最大速度
val rows = ArrayList<Row>()   // 所有行（从顶到底排列）
var rowH: Float = 0f          // 行高
var colW: Float = 0f          // 列宽
var score: Int = 0
var speed: Float = 1f          // 当前滚动速度
var over: Boolean = false
var started: Boolean = false   // 首次点击后为 true
```

## 3. 初始化逻辑

- 铺满屏幕可见区域 +1 行（最顶行在屏幕上方外待进入）
- 每行随机一个列为黑块，`hit=false`
- `score=0`，`speed=1`，`started=false`

## 4. 输入处理

- `ACTION_DOWN` 检测点击列和所在行
- 若 `!started` → `started=true` 开始滚动
- 只有「当前最下方、未点击、列号匹配」的黑块可点
  - `row === lowestUnhit() && c == row.col` → 点击成功
  - 其他点击无效（不扣分、不判负）

## 5. 核心更新循环

```
update(dt):
  若 over 或 !started → return
  step = dt * rowH * speed * 0.9
  所有行 y += step（向下滚动）

  移除已滚出底部的行：
    若该行 !hit → over = true; gameOver("漏掉黑块了！")

  补充新行到顶部：
    while 最顶行.y > -rowH → 在顶部插入新行
```

## 6. 胜负判定

- **失败**：黑块行滚出屏幕底部且从未被点击 → "漏掉黑块了！"
- **无胜利条件**（无尽模式，得分 = 点击数）

## 7. 边界与特殊规则

1. **只能点最下方未点击黑块**：`lowestUnhit()` 找最下方 hit=false 的行
2. **点错不判负**：点击白块或错误列的黑块只忽略，不扣分
3. **速度递增**：每点击一个黑块 `speed += 0.05`，上限 `MAX_SPEED=3.0`
4. **滚动速度**：`step = dt * rowH * speed * 0.9`
5. **已点击行变色**：白底变灰（`whiteHitPaint`），黑块变深灰（`blackHitPaint`）
6. **顶部补行**：保证最顶行始终在屏幕上方 `-rowH` 处等待滑入

## 8. 渲染要求

- 4 列布局，每行一个黑块其余白块
- 已点击行整体变灰
- 中间 3 条竖线分隔列
- 顶部显示得分（深色胶囊背景）
- 底部显示提示"从下往上依次点击黑色方块 · 别踩白块"
