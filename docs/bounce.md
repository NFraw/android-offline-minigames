# 弹一弹（BounceView）

## 1. 游戏类型与视角

- **类型**：单人动作（弹球打砖块）
- **视角**：俯视，全屏
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
companion object {
    const val COLS = 8       // 砖块列数
    const val ROWS = 5       // 砖块行数
}

val blocks = BooleanArray(COLS * ROWS)   // 砖块存活状态（true = 存在）
var ballX, ballY: Float     // 球心坐标
var vx, vy: Float           // 球速度分量
var ballR: Float            // 球半径 = dp(10)
var paddleX: Float          // 挡板中心 X
var paddleW: Float          // 挡板宽度 = width * 0.2
var paddleH: Float          // 挡板高度 = dp(20)
var blockW: Float           // 砖块宽度 = width / COLS
var blockH: Float           // 砖块高度 = height * 0.05
var score: Int              // 当前得分
var over: Boolean           // 游戏结束标志
var speed: Float            // 球基准速度 = height * 0.85
var launched: Boolean       // 球是否已发射
```

## 3. 初始化逻辑（reset）

```kotlin
blocks 全部设为 true          // 40 块砖全部存在
speed = height * 0.85f       // 基准速度
paddleX = width / 2f         // 挡板居中
ballX = paddleX              // 球在挡板正上方
ballY = height - paddleH - ballR - dp(6)  // 球紧贴挡板顶部
launched = false             // 未发射状态
score = 0, over = false
setScore(0), start()
```

## 4. 输入处理

- **滑动**：`paddleX = event.x`（钳制在 `[paddleW/2, width - paddleW/2]`），挡板跟随手指。
- **首次点击（ACTION_DOWN）**：若 `!launched`，调用 `launch()` 发射球。

### 发射逻辑（launch）

```kotlin
launched = true
off = (ballX - paddleX) / (paddleW / 2)   // -1..1，球相对挡板中心的偏移
vx = off * speed * 0.4                     // 水平速度与偏移成正比
// 保证最小水平分量：避免纯垂直运动
if (abs(vx) < speed * 0.18) vx = sign(off) * speed * 0.18
vy = -speed * 0.72                         // 向上发射
```

## 5. 核心更新循环（update(dt)）

每帧执行：

1. **未发射**：球跟随挡板移动（`ballX = paddleX`），提前返回。
2. **位置更新**：`ballX += vx * dt`，`ballY += vy * dt`。
3. **墙壁反弹**：
   - 左墙：`ballX = ballR`，`vx = abs(vx)`
   - 右墙：`ballX = width - ballR`，`vx = -abs(vx)`
   - 顶墙：`ballY = ballR`，`vy = abs(vy)`
4. **挡板碰撞**（窗口判定法）：
   - 条件：`vy > 0`（球下落）且球底进入挡板顶平面且球顶未超出挡板高度窗口且水平距离在范围内
   - 反弹：`vy = -abs(vy)`，`vx` 按球偏离挡板中心的比例计算
   - 最小水平分量保证：`abs(vx) < speed * 0.15` 时设为 `0.15 * speed`
5. **砖块碰撞**（`hitBlock()`）：遍历所有存活砖块，使用**圆-矩形碰撞检测**：
   - 找到球心到矩形最近点的距离 < `ballR` 的砖块
   - 击碎砖块（`blocks[idx] = false`），加 10 分
   - 按穿透深度决定反射轴（`reflect()`），并将球推出砖块
   - 每帧只处理一次碰撞（`return true` 后跳出）
6. **全清检查**：所有砖块击碎 → 胜利。
7. **漏球检查**：`ballY > height + ballR` → 失败。

### 反射逻辑（reflect）

根据穿透深度（`penX` vs `penY`）决定水平还是竖直反射：
- `penX < penY`：水平反射，球推到砖块左/右侧外
- 否则：竖直反射，球推到砖块上/下侧外
- 都很小：按速度分量大小选择反射轴

## 6. 胜负判定

- **胜利**：`blocks.none { it }`（所有砖块击碎），得分为 `score`，消息 `"全部击碎！得分 {score}"`。
- **失败**：球掉出屏幕底部（`ballY > height + ballR`），消息 `"漏球了！得分 {score}"`。

## 7. 边界与特殊规则

- **窗口判定法挡板碰撞**：不使用穿越检测（上一帧在上、本帧在下），而是检查球是否在挡板高度窗口内。比穿越检测更宽容，快速下落时也不会漏接。
- **最小水平分量**：发射和反弹时都保证 `vx` 不会太小，避免球长时间垂直弹跳。
- **单帧单碰撞**：`hitBlock()` 命中一块砖后立即返回，避免一帧内连续击碎多块。
- **球推出砖块**：反射后将球坐标推到砖块外部，防止连续多帧重复命中同一块砖。
- **砖块为 BooleanArray**：与 BreakoutView 的 IntArray（血量）不同，本游戏砖块一击即碎。

## 8. 渲染要求

- 砖块颜色随行数变化：`0xFF4A6CF7 - r * 0x00101010`（越往下越深）。
- 砖块有 2px 间距和 `dp(6)` 圆角。
- 球为白色实心圆，挡板为绿色圆角矩形（`0xFF3ED98A`）。
- 底部文字提示当前操作状态（未发射 / 已发射 + 得分）。
