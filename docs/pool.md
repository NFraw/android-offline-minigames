# 台球（PoolView）

## 1. 游戏类型与视角

- **类型**：双人体育（传统 8 球台球）
- **视角**：俯视，球桌长边跟随设备方向（竖屏竖放、横屏横放）。
- **屏幕方向**：自适应（竖屏/横屏均可，球桌方向跟随）

## 2. 数据模型

```kotlin
data class Ball(
    var x: Float, var y: Float,     // 位置
    var vx: Float, var vy: Float,   // 速度
    val r: Float,                    // 半径
    val num: Int,                    // 球号：0=母球，1-7=实球，8=黑球，9-15=花球
    var pocketed: Boolean            // 是否已落袋
)

class Pocket(
    val x: Float, val y: Float, val r: Float,  // 位置和半径
    val top: Boolean, val bottom: Boolean,       // 位于哪条边
    val left: Boolean, val right: Boolean
)

companion object {
    const val RESTITUTION = 0.82f   // 库边回弹系数
    const val FRICTION = 1.6f       // 台面阻尼（指数衰减）
    const val STOP_SPEED = 3f       // 低于此速度视为静止
    const val PHASE_AIM = 0         // 瞄准阶段
    const val PHASE_PLACE = 1       // 球在手中：放置母球
    const val PHASE_CALL8 = 2       // 打 8 号前选袋口
    const val PHASE_MOVE = 3        // 球在滚动
    val BALL_COLORS = intArrayOf(...)  // 15 球的传统配色
}

val balls = ArrayList<Ball>         // 所有球（含母球）
val pockets = ArrayList<Pocket>     // 6 个袋口
var phase: Int                      // 当前阶段
var turn: Int                       // 当前回合：0=玩家1，1=玩家2
var group1: Int                     // 玩家1 的球组：0=未定，1=实球（1-7），2=花球（9-15）
var breakShot: Boolean              // 是否为开球杆
var ballInHand: Boolean             // 是否处于"球在手中"状态
var kitchenOnly: Boolean            // 球在手中是否限定发球区（仅开球犯规）
var callPocket: Int                 // 8 号球叫袋序号，-1=未叫
var firstContact: Int               // 本杆母球首先碰到的球号，-1=未碰
var need8Shot: Boolean              // 本杆是否处于打 8 号阶段
val shotEvents: ArrayList<Pair<Int, Int>>  // 本杆进球序列 (球号, 袋口序号)
var aimX / aimY: Float              // 瞄准目标点
var dragging: Boolean               // 是否正在拖动瞄准
var showGuide: Boolean              // 是否显示辅助线
var anyMoving: Boolean              // 是否有球在运动
var wasMoving: Boolean              // 上一帧是否有球在运动（用于检测停止）
var over: Boolean

// 布局相关
var tableLeft / tableTop / tableW / tableH: Float  // 球桌区域
var ballR: Float                    // 球半径
var pocketR: Float                  // 袋口半径
var cushionW: Float                 // 木框厚度
var verticalTable: Boolean          // 是否竖屏竖放
```

## 3. 初始化逻辑（reset）

1. 清空 `balls`。
2. 添加母球（`num=0`）到起始位置（`cueStartX/Y`，竖屏时在底部 90% 处，横屏时在左侧 10% 处）。
3. 调用 `buildRack()` 构建三角架：
   - 8 号球居中（第 5 个位置，索引 4）
   - 后两角一实一花（随机取）
   - 其余 12 球随机排列
   - 三角架位于脚点（`footX/Y`，远离发球区一侧）
4. `turn = 0`，`group1 = 0`（桌面开放），`breakShot = true`，`ballInHand = false`，`kitchenOnly = false`。
5. `callPocket = -1`，`firstContact = -1`，`need8Shot = false`，`shotEvents.clear()`。
6. `anyMoving = false`，`wasMoving = false`，`over = false`，`phase = PHASE_AIM`。
7. 调用 `start()`。

## 4. 输入处理

- **触发条件**：`!over && !paused`
- **辅助线按钮**：右下角按钮切换 `showGuide`
- **PHASE_PLACE（球在手中）**：点击台面放置母球
  - 坐标钳制到台面内
  - 若 `kitchenOnly`：竖屏限制在发球区以下（`y >= headY() + cue.r`），横屏限制在发球区以左
  - 就近搜索不与任何球重叠的落点（螺旋搜索）
- **PHASE_CALL8（叫袋）**：点击袋口选择 8 号球进哪个袋
- **PHASE_AIM（瞄准）**：
  - `ACTION_DOWN`：记录起始点，开始拖动
  - `ACTION_MOVE`：更新瞄准点
  - `ACTION_UP`：调用 `shoot()` 击球

### 击球（shoot）
- 计算方向：从母球到触摸点的反方向
- 力度：`power = len / (tableW * 0.5)`，钳制到 `[0.15, 1.0]`
- 速度：`speed = tableH * 2.8 * power`
- 设置母球速度，清除本杆记录，进入 `PHASE_MOVE`

## 5. 核心更新循环（update(dt)）

```
if over: return
// 子步进（sub=2），每子步 dt/2
repeat(2):
    for each ball:
        位置 += 速度 × sdt
        检测落袋 → captureIfInPocket
        检测库边反弹 → reflectCushion
        摩擦阻尼：速度 × exp(-FRICTION × sdt)
        速度 < STOP_SPEED → 静止
    球球碰撞 → collideBalls
// 检测所有球是否停止
anyMoving = balls.any { !pocketed && (vx != 0 || vy != 0) }
if wasMoving && !anyMoving:
    wasMoving = false
    resolveShot()  // 一杆结束，结算
```

### 物理细节
- **库边反弹**：速度取反 × `RESTITUTION`（0.82），位置钳制到台面内
- **球球碰撞**：等质量弹性碰撞，记录母球首先碰到的球（`firstContact`），碰撞声效阈值 `|vn| > 60`
- **落袋判定**：球心距袋口中心 < 袋口半径，且球朝袋口方向运动或已在台面外，且靠近对应墙壁
- **摩擦阻尼**：`exp(-FRICTION * sdt)` 指数衰减

## 6. 胜负判定

### 一杆结束后的结算（resolveShot）

#### 开球阶段（breakShot）
- 8 号进袋：不算胜负，摆回脚点（`respotEight`）
- 犯规/母球落袋：对方"球在手中"（限定发球区）
- 进球但未犯规：继续击球（桌面保持开放）
- 未进球：换手

#### 桌面开放时（group1 == 0）
- 首次合法进球确定球组：进球的球号决定该玩家是实球（1-7）还是花球（9-15）

#### 8 号球进袋判定
- **合法获胜**：不犯规 + 己方球组已清光 + 进了所叫袋口
- **犯规判负**：犯规时进 8 号、未清光球组就进 8 号、8 号未进所叫袋口

#### 犯规判定（isFoul）
- 母球落袋
- 母球未碰到任何球（`firstContact == -1`）
- 已分组后先碰对方球组
- 打 8 号阶段先碰的不是 8 号球

#### 犯规处理
- 对方获得"球在手中"（`ballInHand = true`）
- 非开球犯规时可任意摆放（`kitchenOnly = false`）
- 开球犯规限定发球区（`kitchenOnly = true`）

#### 换手规则
- 犯规 → 换手
- 未犯规但未打进己方球 → 换手
- 未犯规且打进己方球 → 续击

## 7. 边界与特殊规则

- **开球 8 号不判负**：开球时 8 号进袋摆回脚点，不算输赢。
- **脚点位置**：竖屏时在球桌顶部 18% 处，横屏时在右侧 82% 处。
- **发球区**：竖屏时底部 22%，横屏时左侧 22%。
- **叫袋**：只有打 8 号阶段需要叫袋（`PHASE_CALL8`），点击袋口后才进入瞄准。
- **球组判定**：`ballGroup(num)` — 1-7 → 1（实球），8 → 3，9-15 → 2。
- **辅助线**：从母球中心沿击球方向延伸，到首个目标球边缘或库边截止。可开关。
- **球杆绘制**：位于母球后方（击球方向反侧），长度 `tableW * 0.45`。
- **碰撞声效**：`|vn| > 60` 播放 CLICK 音效。
- **母球落袋后**：标记 `pocketed = true`，下一阶段为 `PHASE_PLACE`。
- **respotEight**：8 号球摆回脚点，若该位置有球则随机寻找空位。

## 8. 渲染要求

- 球桌：木框（`cushionPaint`，`0xFF8A5A2B`）+ 绿色台面（`feltPaint`，`0xFF1F8A4C`）+ 6 个黑色袋口。
- 发球线：半透明白色虚线。
- 实球（1-7）：纯色圆 + 白色号码圆 + 数字。
- 花球（9-15）：白色底 + 色带（中间 36% 高度）+ 白色号码圆 + 数字。
- 8 号球：纯黑。
- 母球：纯白 + 高光点。
- 球杆：木色（`0xFFB08D57`）+ 浅色线条（`0xFFE8D9B0`）。
- 辅助线：白色虚线，可开关。首个目标球高亮黄色圆环。
- 顶部状态条：当前玩家、球组、状态信息。
- 叫袋阶段：6 个袋口高亮黄色描边 + 序号。
- 球在手中：台面半透明遮罩。
