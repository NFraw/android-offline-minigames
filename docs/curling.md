# 冰壶对决（CurlingView）

## 1. 游戏类型与视角

- **类型**：多人体育（冰壶 / Curling），支持 2/3/4 人同屏轮流
- **视角**：俯视，冰面纵向布局。圆心（大本营）在上方约 28% 处，投壶区在底部。
- **屏幕方向**：竖屏

## 2. 数据模型

### CurlingView

```kotlin
companion object {
    const val STONES_PER_PLAYER = 2  // 每人每局投壶数
}

enum class Phase { SELECT, THROW, MOVING, ROUND_END, OVER }

// 物理引擎
lateinit var physics: CurlingPhysics

// 冰面布局
var centerX / centerY: Float  // 圆心（大本营中心）
var ringR: Float              // 大本营半径
var stoneR: Float             // 冰壶半径
val sheet: RectF              // 冰面区域

// 游戏状态
var phase: Phase              // 当前阶段
var players: Int              // 玩家人数（2/3/4）
var turn: Int                 // 当前投壶玩家
var round: Int                // 当前局数
var rounds: Int               // 总局数（2人3局、3人4局、4人5局）
var lastWinner: Int           // 上一局赢家（下一局先投者）
val score: IntArray(4)        // 各玩家总分
val thrownCount: IntArray(4)  // 各玩家本局已投数
var throwLeft: Int            // 本局剩余投壶总数
var roundEndT: Float          // 回合结束计时器（2.6 秒后进入下一局）
var roundResult: String       // 本轮结果文字
var ranking: String           // 最终排名文字

// 蓄力/瞄准
var charging: Boolean         // 是否正在蓄力
var chargeT: Float            // 蓄力计时器
var power: Float              // 当前力度（0..1，正弦摆动）
var launchX: Float            // 投出位置 X（沿底部左右滑动）
var aimCurX / aimCurY: Float  // 当前触点（决定投出方向）
```

### CurlingPhysics

```kotlin
class Stone(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val player: Int
) {
    val moving: Boolean get() = vx != 0f || vy != 0f
}

val stones: ArrayList<Stone>  // 所有冰壶
var left / top / right / bottom: Float  // 边界矩形
var lastImpactSpeed: Float    // 本 step 最强碰撞冲击速度
val stoneR: Float             // 冰壶半径
val restitution: Float        // 碰撞回弹系数（默认 0.4）
```

## 3. 初始化逻辑（reset / startMatch）

### reset
1. 清空物理引擎，清零分数和投壶计数。
2. `round = 1`，`throwLeft = players * STONES_PER_PLAYER`，`turn = 0`。
3. `charging = false`，`power = 0`，`roundResult = ""`，`ranking = ""`。
4. 若 `players == 0` → `phase = SELECT`（选人数），否则 → `phase = THROW`。
5. 调用 `start()`。

### startMatch（选人数后）
1. 清空物理引擎和分数。
2. `rounds = players + 1`（2人3局、3人4局、4人5局）。
3. `lastWinner = 0`，`turn = 0`，`phase = THROW`。

### 局间重置
- `round++`，清空物理引擎，重置各玩家 `thrownCount`。
- `throwLeft = players * STONES_PER_PLAYER`。
- `turn = lastWinner`（上一局赢家先投）。

## 4. 输入处理

- **SELECT 阶段**：点击 2/3/4 人按钮选择玩家人数
- **THROW 阶段**：
  - `ACTION_DOWN`：开始蓄力（`charging = true`），记录触点作为出发点和瞄准点
  - `ACTION_MOVE`：更新触点（`aimCurX/Y`），出发点跟随触点 X 坐标（`launchX`）
  - `ACTION_UP`：若 `power > 0.08` 则调用 `throwStone()` 投壶

### 投壶（throwStone）
- 起点：`(launchX, sheet.bottom - stoneR)`
- 方向：从起点指向当前触点（`aimCurX - sx, aimCurY - sy`），归一化
  - 若未拖出方向：兜底朝圆心方向
- 速度：`power * sheet.height() * 1.35`
- 调用 `physics.addStone(sx, sy, dx*v, dy*v, turn)`
- 进入 `PHASE_MOVING`

## 5. 核心更新循环（update(dt)）

```
when (phase):
    THROW:
        if charging:
            chargeT += dt
            power = 0.5 + 0.5 * sin(chargeT * 3.0)  // 0..1 正弦摆动
    MOVING:
        anyMoving = physics.step(dt, 7f)
        if physics.lastImpactSpeed >= 90f: playSound(CLICK)
        if physics.lastImpactSpeed >= 220f: buzz()
        if !anyMoving:
            throwLeft--, thrownCount[turn]++
            if throwLeft > 0: turn = nextPlayer(turn), phase = THROW
            else: scoreRound(), phase = ROUND_END
    ROUND_END:
        roundEndT += dt
        if roundEndT >= 2.6s:
            if round >= rounds: finishMatch()
            else: 进入下一局
```

### 物理引擎（CurlingPhysics.step）
- 子步进：根据最大速度计算子步数（每步位移 ≤ 石径一半），最多 16 子步
- 每子步：移动位置 → 碰撞冲量 → 分离重叠 → 边界约束
- 摩擦阻尼：以 60fps 为基准，每帧 ×0.987，随 dt 归一化
- 速度 < `stopSpeed`(7) → 静止

## 6. 胜负判定

### 单局计分（scoreRound）
- 找到离圆心最近的玩家（`bestP`）
- 找到次近玩家的距离（`second`）
- 该玩家所有比次近距离更近的壶，每壶计 1 分
- `lastWinner = bestP`（下局先投者）

### 全场结束（finishMatch）
- 按分数降序排名
- 最高分者为冠军；多人同分则并列冠军
- 结算消息包含完整排名

## 7. 边界与特殊规则

- **蓄力机制**：`power = 0.5 + 0.5 * sin(chargeT * 3.0)`，在 0..1 之间正弦摆动，松手时取当前值。
- **投出方向**：由起点和当前触点两点决定（"瞄准线即球路"），不是预先指向圆心。
- **出发点**：沿底部投壶线左右滑动自定义，钳制到冰面内。
- **碰撞物理**：
  - 等质量弹性碰撞，回弹系数 `restitution = 0.4`
  - 冲量公式：`j = -(1+e) * rel / 2`
  - 子步进防穿透：每步位移 ≤ 石径一半
  - 重叠分离：均摊位移
- **边界约束**：碰壁后速度取反 ×0.25（几乎不弹回）。
- **摩擦模型**：`f = 0.987^(60*dt)`，基于 60fps 归一化。
- **碰撞音效/震动**：冲击速度 ≥90 播放 CLICK，≥220 触发震动。
- **局数规则**：2人3局、3人4局、4人5局（`rounds = players + 1`）。
- **先投权**：首局玩家 0 先投，之后每局上一局赢家先投。
- **回合结束等待**：2.6 秒后自动进入下一局或结算。

## 8. 渲染要求

- 冰面：浅蓝圆角矩形（`0xFFE8F4FB`），边框线（`0xFFC9DEEE`）。
- 大本营：4 层同心圆（蓝-白-红-白），中心红点。
- 中线：水平线穿过圆心。
- 投壶区提示线：底部区域的水平线。
- 冰壶：按玩家颜色（红/蓝/绿/金），带编号和高光点。
- 蓄力预览：黄色虚线瞄准线 + 起点/触点标记 + 虚拟冰壶 + 蓄力条。
- 顶部状态：当前局数、玩家、剩余壶数。
- 记分板：顶部彩色圆点 + 分数，当前玩家高亮。
- 局间信息：深色文字（浅色冰面上），显示得分和下局先投者。
- 选人界面：半透明遮罩 + 2/3/4 人按钮。
