# 四人反应力（Reaction4View）

## 1. 游戏类型与视角

- **类型**：多人反应力测试（四人同屏）
- **视角**：屏幕均分为 2×2 四个象限，每个象限对应一名玩家。左上=玩家1，右上=玩家2，左下=玩家3，右下=玩家4。
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
companion object {
    const val WIN_SCORE = 5   // 先到此分获胜
    const val WAIT = 0        // 等待阶段（灰色，不能点击）
    const val READY = 1       // 变绿阶段（可以点击得分）
    const val SETTLED = 2     // 结算过渡阶段（0.9 秒后进入下一轮）
}

var state: Int               // 当前状态（WAIT / READY / SETTLED）
var waitTimer: Float         // 等待倒计时（随机 0.8~2.6 秒）
var readyTimer: Float        // 变绿倒计时（2.0 秒，超时未点则进入结算）
var settleTimer: Float       // 结算过渡计时器（0.9 秒）
val scores: IntArray(4)      // 各玩家得分
val scored: BooleanArray(4)  // 本回合该分区是否已得分
val flashes: BooleanArray(4) // 本回合该分区是否抢拍被罚
```

## 3. 初始化逻辑（reset）

1. `state = WAIT`，`waitTimer = 1.2` 秒（初始等待）。
2. `readyTimer = 0`，`settleTimer = 0`。
3. `scores` 全部为 0，`scored` 和 `flashes` 全部为 false。
4. 调用 `start()`。

### 新一轮（newRound）
- `state = WAIT`，`waitTimer = 0.8 + random() * 1.8`（随机 0.8~2.6 秒）
- `scored` 和 `flashes` 全部重置为 false

## 4. 输入处理

- **触发条件**：`ACTION_DOWN` 或 `ACTION_POINTER_DOWN`（支持多指同时点击），且 `!paused`
- **区域判定**（`zoneOf`）：根据触摸坐标判断象限
  - `x < width/2 && y < height/2` → 0（左上）
  - `x >= width/2 && y < height/2` → 1（右上）
  - `x < width/2 && y >= height/2` → 2（左下）
  - `x >= width/2 && y >= height/2` → 3（右下）
- **READY 状态**：
  - 该分区已得分或已抢拍被罚 → 忽略
  - 否则：`scored[zone] = true`，`scores[zone]++`，播放 EAT 音效
  - 达到 `WIN_SCORE`（5 分）→ 游戏结束
  - 所有分区都已完成（`allZonesDone`）→ 进入 `SETTLED`
- **WAIT 状态**：
  - 点击 = 抢拍：`flashes[zone] = true`，播放 LOSE 音效
  - 抢拍只罚该分区，不影响其他分区继续

### allZonesDone
- 遍历 4 个分区，若每个分区都已得分或已抢拍 → 返回 true

## 5. 核心更新循环（update(dt)）

```
when (state):
    WAIT:
        waitTimer -= dt
        if waitTimer <= 0:
            state = READY
            readyTimer = 2.0
            playSound(MOVE)  // 提示音：可以点了
    READY:
        readyTimer -= dt
        if readyTimer <= 0:
            state = SETTLED
            settleTimer = 0.9
    SETTLED:
        settleTimer -= dt
        if settleTimer <= 0: newRound()
```

## 6. 胜负判定

- **胜利条件**：任一玩家得分达到 `WIN_SCORE`（5 分）
- **判定时机**：`onTouch` 中得分后立即检查
- **结算消息**：`"玩家 {N} 率先拿到 5 分获胜！"`

## 7. 边界与特殊规则

- **多指同时点击**：支持 `ACTION_POINTER_DOWN`，通过 `event.actionIndex` 获取触摸点索引，每个触摸点独立判定区域。四指同时点击四个区，四区都可得分。
- **抢拍规则**：在 WAIT 状态下点击 = 抢拍，该分区本轮标记 `flashes`，不能再得分（`scored` 和 `flashes` 互斥检查）。不影响其他分区。
- **每区每轮只计一次分**：`scored[zone]` 为 true 后再次点击无效。
- **变绿持续时间**：2.0 秒，超时后自动进入结算。
- **等待时间随机**：每轮 `0.8 + random() * 1.8` 秒，防止预判。
- **结算过渡**：SETTLED 状态持续 0.9 秒，让玩家看到结果后再进入下一轮。
- **READY 阶段全部分区完成**：当所有分区都已得分或被罚，立即进入 SETTLED（不等 readyTimer 超时）。

## 8. 渲染要求

- 四个象限用分割线（半透明白色）分隔。
- WAIT 状态：深灰色背景（`0xFF2A3557`），显示"等待…"
- READY 状态：绿色背景（`0xFF2E9E5B`），显示"点击！"
- 抢拍被罚：红色背景（`0xFFB3392B`），显示"抢拍！"
- 已得分：显示"+1"
- 每个象限中心显示玩家编号和当前得分。
- 底部居中显示规则提示。
