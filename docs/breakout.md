# 打砖块（BreakoutView）

## 1. 游戏类型与视角

- **类型**：单人动作（经典打砖块）
- **视角**：俯视，全屏
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
companion object {
    const val COLS = 10      // 砖块列数
    const val ROWS = 6       // 砖块行数
}

val blocks = IntArray(COLS * ROWS)   // 砖块血量（0 = 已碎，1/2/3 = 剩余血量）
var ballX, ballY: Float     // 球心坐标
var vx, vy: Float           // 球速度分量
var ballR: Float            // 球半径 = dp(9)
var paddleX: Float          // 挡板中心 X
var paddleW: Float          // 挡板宽度 = width * 0.22
var paddleH: Float          // 挡板高度 = dp(20)
var blockW: Float           // 砖块宽度 = width / COLS
var blockH: Float           // 砖块高度 = height * 0.045
var score: Int              // 当前得分
var over: Boolean           // 游戏结束标志
var speed: Float            // 球基准速度 = height * 0.9
```

## 3. 初始化逻辑（reset）

```kotlin
// 砖块血量：前 2 行 = 3，中间 2 行 = 2，最后 2 行 = 1
for (r in 0 until ROWS) for (c in 0 until COLS)
    blocks[r * COLS + c] = if (r < 2) 3 else if (r < 4) 2 else 1

ballX = width / 2f
ballY = height * 0.75f      // 球初始位置偏下
speed = height * 0.9f
vx = speed * 0.35f           // 初始水平速度
vy = -speed * 0.75f          // 初始竖直速度（向上）
paddleX = width / 2f         // 挡板居中
score = 0, over = false
setScore(0), start()
```

**关键区别**：与 BounceView 不同，球在 `reset()` 时**立即发射**，无等待点击阶段。

## 4. 输入处理

- **滑动 / 按下**：`paddleX = event.x`（钳制在 `[paddleW/2, width - paddleW/2]`），挡板跟随手指。
- 无发射逻辑 —— 球一开始就运动。

## 5. 核心更新循环（update(dt)）

每帧执行：

1. **位置更新**：`ballX += vx * dt`，`ballY += vy * dt`。
2. **墙壁反弹**：
   - 左墙：`ballX = ballR`，`vx = abs(vx)`
   - 右墙：`ballX = width - ballR`，`vx = -abs(vx)`
   - 顶墙：`ballY = ballR`，`vy = abs(vy)`
3. **挡板碰撞**（窗口判定法，与 BounceView 一致）：
   - 条件：`vy > 0` 且球在挡板高度窗口内且水平距离在范围内
   - 反弹：`vy = -abs(vy)`，`vx = 偏移比例 * speed * 0.65`
   - **无最小水平分量保证**（与 BounceView 不同）
4. **砖块碰撞**（`hitBlock()`）：遍历所有血量 > 0 的砖块，使用圆-矩形碰撞检测：
   - 命中后 `blocks[idx]--`（扣 1 点血量），加 10 分
   - 按穿透深度反射（`reflect()`），推出球
   - 每帧只处理一次碰撞
5. **全清检查**：`blocks.none { it > 0 }` → 胜利。
6. **漏球检查**：`ballY > height + ballR` → 失败。

### 反射逻辑（reflect）

与 BounceView 完全一致：根据 `penX` vs `penY` 决定反射轴，推出球避免重复命中。

## 6. 胜负判定

- **胜利**：所有砖块血量归零，得分为 `score`，消息 `"全部击碎！得分 {score}"`。
- **失败**：球掉出屏幕底部，消息 `"漏球了！得分 {score}"`。

## 7. 边界与特殊规则

- **砖块多血量**：与 BounceView 的 BooleanArray 不同，本游戏砖块有 1~3 点血量，需多次命中才能击碎。
- **砖块颜色按血量变化**：
  - 血量 3 → 蓝色 `0xFF4A6CF7`
  - 血量 2 → 橙色 `0xFFF5A54A`
  - 血量 1 → 红色 `0xFFE8503A`
- **球立即发射**：`reset()` 后球立刻运动，玩家需快速反应。
- **挡板宽度**：`width * 0.22`，比 BounceView（`width * 0.2`）略宽。
- **球速**：`height * 0.9`，比 BounceView（`height * 0.85`）略快。
- **挡板反弹无最小 vx 保证**：球可能长时间垂直弹跳（与 BounceView 的设计差异）。

## 8. 渲染要求

- 砖块按血量着色（红/橙/蓝），有 `dp(5)` 圆角和 2px 间距。
- 球为白色实心圆（半径 `dp(9)`）。
- 挡板为浅蓝色圆角矩形（`0xFF37C2F0`）。
- 底部显示 `"滑动挡板 · 得分 {score}"`。
