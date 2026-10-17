# 反应力（Reaction）游戏逻辑文档

本文档涵盖两个版本：**ReactionView**（双人对决）和 **Reaction4View**（四人反应力）。

## 1. 游戏类型与视角

### ReactionView（双人对决）
- **类型**：双人实时反应
- **视角约定**：屏幕上下分两半，玩家 1 在上半屏，玩家 2 在下半屏。屏幕变绿后先点击自己半屏者得分。
- **屏幕方向**：竖屏

### Reaction4View（四人反应力）
- **类型**：四人实时反应
- **视角约定**：屏幕分为四个象限（2×2），左上=玩家1、右上=玩家2、左下=玩家3、右下=玩家4。四区同时变绿，各点各的区可同时得分。
- **屏幕方向**：竖屏

## 2. 数据模型

### ReactionView

```kotlin
private const val WIN_SCORE = 10     // 获胜分数
private const val WAIT = 0           // 等待状态（未变绿）
private const val READY = 1          // 就绪状态（已变绿，可点击）
private const val SETTLED = 2        // 结算状态（本轮已出结果）

private var state = WAIT
private var waitTimer = 0f           // 等待倒计时（随机 0.8~2.6 秒）
private var settleTimer = 0f         // 结算展示时间（1.0 秒）
private var s1 = 0                   // 玩家1 分数
private var s2 = 0                   // 玩家2 分数
private var earlyFlash1 = false      // 玩家1 本轮抢拍
private var earlyFlash2 = false      // 玩家2 本轮抢拍
private var lastWinner = -1          // 本轮得分方（0=玩家1, 1=玩家2）
```

### Reaction4View

```kotlin
private const val WIN_SCORE = 5      // 获胜分数
private const val WAIT = 0
private const val READY = 1
private const val SETTLED = 2

private var state = WAIT
private var waitTimer = 0f           // 等待倒计时（随机 0.8~2.6 秒）
private var readyTimer = 0f          // 就绪持续时间（2.0 秒）
private var settleTimer = 0f         // 结算展示时间（0.9 秒）
private val scores = IntArray(4)     // 四位玩家分数
private val scored = BooleanArray(4) // 本回合该分区已得分
private val flashes = BooleanArray(4)// 本回合该分区抢拍被罚
```

## 3. 初始化逻辑（reset）

### ReactionView
1. `state = WAIT`，`waitTimer = 1.2f`
2. `settleTimer = 0f`
3. `s1 = s2 = 0`
4. `earlyFlash1 = earlyFlash2 = false`，`lastWinner = -1`
5. 调用 `start()`

### Reaction4View
1. `state = WAIT`，`waitTimer = 1.2f`
2. `readyTimer = settleTimer = 0f`
3. 所有分数和状态数组清零
4. 调用 `start()`

## 4. 输入处理

### ReactionView

- 支持多指（ACTION_DOWN + ACTION_POINTER_DOWN）
- 判断触摸属于上半屏（玩家1）还是下半屏（玩家2）
- **READY 状态**：该玩家得分，切换到 SETTLED
- **WAIT 状态**：抢拍！对方得分，标记 `earlyFlash`，切换到 SETTLED

### Reaction4View

- 支持多指（ACTION_DOWN + ACTION_POINTER_DOWN）
- 根据触摸坐标判定属于哪个象限（`zoneOf(x, y)`）
- **READY 状态**：
  - 该分区已得分或已被罚 → 忽略
  - 否则该分区得分 `scores[zone]++`
  - 达到 WIN_SCORE → 游戏结束
  - 所有分区都完成（得分或被罚）→ 切换到 SETTLED
- **WAIT 状态**：
  - 抢拍：该分区标记 `flashes[zone] = true`，本轮该分区不得分
  - 播放 LOSE 音效

## 5. 核心更新循环（update(dt)）

### ReactionView

```
WAIT → waitTimer -= dt
  waitTimer <= 0 → state = READY, 播放 MOVE 音效
SETTLED → settleTimer -= dt
  settleTimer <= 0 → newRound()
```

### Reaction4View

```
WAIT → waitTimer -= dt
  waitTimer <= 0 → state = READY, readyTimer = 2.0f, 播放 MOVE 音效
READY → readyTimer -= dt
  readyTimer <= 0 → state = SETTLED, settleTimer = 0.9f
SETTLED → settleTimer -= dt
  settleTimer <= 0 → newRound()
```

### newRound()
- `state = WAIT`
- `waitTimer = 0.8f + random() * 1.8f`（随机 0.8~2.6 秒）
- 清除本轮标记

## 6. 胜负判定

### ReactionView
- **即时判定**：每次得分后检查 `s1 >= 10` 或 `s2 >= 10`
- **结算**：`GameResult(0, p1Win, "玩家 X 率先拿到 10 分获胜！")`

### Reaction4View
- **即时判定**：某玩家得分后检查 `scores[zone] >= 5`
- **结算**：`GameResult(0, true, "玩家 X 率先拿到 5 分获胜！")`

## 7. 边界与特殊规则

### ReactionView
- ⚠️ **抢拍惩罚**：在 WAIT 状态点击，对方直接得分（不是自己扣分）。
- ⚠️ **先到先得**：READY 状态下谁先点击谁得分，不区分反应速度差多少。
- ⚠️ **等待时间随机**：`0.8 + random() * 1.8` 秒，防止预判节奏。

### Reaction4View
- ⚠️ **独立分区**：四个分区各自独立计分，抢拍只影响被罚分区，其他分区继续正常。
- ⚠️ **每区每轮一次**：`scored[zone]` 和 `flashes[zone]` 保证每区每轮最多得一次分。
- ⚠️ **就绪持续 2 秒**：READY 状态持续 2 秒，超时后未点击的分区本轮不得分。
- ⚠️ **抢拍不扣分**：抢拍只是该分区本轮作废（`flashes[zone] = true`），不扣已有分数。
- ⚠️ **全部完成提前结算**：所有 4 个分区都得分或被罚后，提前进入 SETTLED，不等 readyTimer 超时。

## 8. 渲染要求

### ReactionView
- **WAIT 状态**：上下两半均为深灰（`0xFF2A3557`），显示"等待…"
- **READY 状态**：上下两半均为绿色（`0xFF2E9E5B`），显示"点击！"
- **SETTLED 状态**：得分方绿色显示"+1"，抢拍方红色（`0xFFB3392B`）显示"抢拍！"
- **分数**：顶部显示"玩家1 X : Y 玩家2"
- **分隔线**：中间水平线

### Reaction4View
- **四个象限**：各自独立显示颜色和状态
- **WAIT**：深灰，"等待…"
- **READY**：绿色，"点击！"
- **得分**：显示"+1"
- **抢拍**：红色，"抢拍！"
- **分数**：每个象限内显示"玩家 N：X"
- **分隔线**：十字线分隔四区
