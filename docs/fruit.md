# 切水果（FruitNinjaView）

## 1. 游戏类型与视角

- **类型**：单人动作（水果忍者）
- **视角**：俯视，全屏
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
// 水果规格表（companion object）
data class FruitSpec(val emoji: String, val value: Int, val sizeMul: Float)
val FRUITS: List<FruitSpec>  // 8 种水果：🍉20 🍊15 🍎15 🍌10 🍇10 🥝10 🍓5 🍒5

const val STAGE_LEN = 30f    // 每阶段时长（秒）
const val MAX_LIVES = 3      // 初始生命数

// 运行时对象
data class Fruit(
    val emoji: String, val value: Int,
    var x: Float, var y: Float,       // 位置
    var vx: Float, var vy: Float,     // 速度
    var r: Float,                      // 碰撞半径
    var cut: Boolean = false,          // 是否已被切
    val bomb: Boolean = false          // 是否为炸弹
)

val fruits = ArrayList<Fruit>          // 场上所有水果/炸弹
var grav: Float                        // 重力加速度 = height * 2.2f
var spawnTimer: Float                  // 生成倒计时
var score: Int                         // 当前得分
var lives: Int                         // 剩余生命（初始 3）
var stage: Int                         // 当前阶段（从 1 开始）
var stageTimer: Float                  // 阶段计时器
var best: Int                          // 历史最高分
var over: Boolean

// 滑动追踪
var lastX, lastY: Float               // 上一帧触摸坐标
var hasLast: Boolean                   // 是否有有效上一帧

// 刀痕轨迹
data class TrailPt(val x: Float, val y: Float, var t: Float)
val trail = ArrayList<TrailPt>        // 轨迹点列表
const val TRAIL_LIFE = 0.32f          // 单点寿命（秒）
```

## 3. 初始化逻辑（reset）

```kotlin
fruits.clear()
spawnTimer = 0.6f          // 首次生成延迟 0.6 秒
score = 0
lives = MAX_LIVES           // 3 条命
stage = 1
stageTimer = 0f
best = ScoreStore(context).best("fruit")
over = false
hasLast = false
trail.clear()
setScore(0)
start()
```

## 4. 输入处理

- **ACTION_DOWN**：记录坐标 `lastX/lastY`，清空旧轨迹，添加第一个轨迹点。
- **ACTION_MOVE**：添加轨迹点；对所有未切水果做**线段-圆碰撞检测**（`lineHitsCircle`）：
  - 碰撞容差 `r * 1.1f`（比视觉半径大 10%）
  - 碰撞后：
    - 水果：`cut = true`，加对应分值，播放吃音效
    - 炸弹：`cut = true`，扣一条命，震动反馈；若命归零则结束游戏
- **ACTION_UP / ACTION_CANCEL**：清除 `hasLast`，轨迹靠淡出自然消失。

### 线段-圆碰撞（lineHitsCircle）

将线段投影到圆心，取最近点，判断距离是否小于半径。线段长度 < 1px 时退化为点-圆距离。

## 5. 核心更新循环（update(dt)）

每帧执行：

1. **刀痕老化**：遍历 `trail`，每点 `t -= dt`，过期移除。
2. **阶段推进**：`stageTimer += dt`，满 30 秒进入下一阶段（`stage++`），显示 Toast 提示。
3. **生成计时**：`spawnTimer -= dt`，归零时调用 `spawnBatch()` 并重置间隔。
4. **物理更新**：遍历所有水果：
   - `vy += grav * dt`（重力）
   - `x += vx * dt`，`y += vy * dt`
   - **左右边界反弹**：碰壁后 `vx` 反向并衰减 0.8 倍
   - **顶部边界回落**：触顶后 `vy` 反向并衰减 0.5 倍
   - **出底移除**：`y > height * 1.15f` 时移除。若未切且非炸弹，扣一条命；命归零则结束。

### 生成逻辑（spawnOne / spawnBatch）

- **spawnInterval**：`max(0.4f, 1.1f - stage * 0.1f) + random * 0.35f`，阶段越高间隔越短。
- **spawnBatch**：stage ≥ 4 时有 35% 概率一次生成 2 个，否则 1 个。
- **炸弹概率**：stage 1 = 0%，stage 2 = 12%，stage 3 = 20%，stage 4+ = 28%。
- **速度倍率**：`1 + (stage - 1) * 0.09`。
- **初始位置**：`y = height * 1.05f`（屏幕底部稍下方），`x` 在屏宽 12%~88% 范围随机。
- **初速度**：水平随机（`±width * 0.25 * speedMul`），竖直向上由重力和目标高度反算（`vy0 = -√(2·g·rise)`），目标弧顶在屏高 35%~85% 处。

## 6. 胜负判定

- **失败条件**（任一触发即结束）：
  1. 生命归零（漏掉 3 个水果 或 切中炸弹导致）
  2. 切中炸弹且命归零
- **结束消息**：包含得分、最高分、到达阶段数
- **无胜利条件**：纯生存模式，目标是尽可能得高分

## 7. 边界与特殊规则

- **炸弹 vs 水果**：炸弹 emoji 为 `💣`，值为 0，碰撞后不加分只扣命。
- **已切水果不参与碰撞**：`if (f.cut) continue`。
- **已切水果仍渲染但跳过**：实际上 `cut` 的水果在 `drawGame` 中 `if (f.cut) continue` 跳过绘制。
- **水果碰壁反弹**：左右壁反弹衰减 0.8，顶部反弹衰减 0.5，确保水果不会飞出屏幕两侧。
- **重力自适应**：`grav = height * 2.2f`，不同屏幕高度下弧线比例一致。
- **刀痕淡出**：每点独立计时，`TRAIL_LIFE = 0.32f` 秒后消失。绘制时按段的平均剩余寿命计算透明度。

## 8. 渲染要求

- 水果用 emoji 绘制（`textSize = r * 2.2f`），炸弹用深色圆 + 描边 + 高光点。
- 刀痕为白色半透明线条（`strokeWidth = dp(6)`），按段的平均寿命淡出。
- 顶部 HUD：得分、最高分（超过时带 🎉）、生命（❤ 符号）、当前阶段。
- 底部提示：`"滑动切割水果得分 · 避开 💣 · 漏掉水果或切到炸弹扣命"`。
