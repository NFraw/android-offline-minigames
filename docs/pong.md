# 乒乓（Pong）游戏逻辑文档

## 1. 游戏类型与视角

- **类型**：双人实时对战
- **视角约定**：玩家 1 在屏幕上半区，球拍位于顶部；玩家 2 在下半区，球拍位于底部。各自在自己半屏拖动控制球拍。
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
// 球拍位置（x 坐标中心）
private var p1y = 0f    // 玩家1 球拍 x 中心
private var p2y = 0f    // 玩家2 球拍 x 中心

// 球的位置与速度
private var ballX = 0f
private var ballY = 0f
private var vx = 0f     // 水平速度
private var vy = 0f     // 垂直速度

// 分数
private var s1 = 0      // 玩家1 得分
private var s2 = 0      // 玩家2 得分

private var over = false
private var speed = 0f  // 当前基准速度（初始 BASE_SPEED=620）

// 触控指针
private var pointerP1 = -1   // 玩家1 手指 ID
private var pointerP2 = -1   // 玩家2 手指 ID

// 布局参数
private var paddleW = 0f     // 球拍宽度
private var paddleH = 0f     // 球拍高度
private var ballR = 0f       // 球半径
private var topY = 0f        // 玩家1 球拍 y 坐标
private var bottomY = 0f     // 玩家2 球拍 y 坐标

// 常量
private const val WIN_SCORE = 5      // 获胜分数
private const val BASE_SPEED = 620f  // 初始球速
private const val MAX_SPEED = 2400f  // 最大球速
private const val ACCEL = 1.07f      // 每次碰撞加速系数
```

## 3. 初始化逻辑（reset）

1. 分数清零：`s1 = 0`，`s2 = 0`
2. `over = false`
3. 速度设为 `BASE_SPEED`（620）
4. 球拍居中：`p1y = p2y = width / 2f`
5. 调用 `resetBall()` 发球
6. 调用 `start()` 启动帧循环

### resetBall()
- 球置于屏幕中心 `(width/2, height/2)`
- 随机选择水平方向（左或右）
- 初始速度：`vx = dir * speed * 0.6`，`vy = ±speed * 0.8`（随机上下）

## 4. 输入处理

### ACTION_DOWN / ACTION_POINTER_DOWN
- 触摸上半屏：绑定为玩家 1 控制，`p1y` 设为触摸点 x 坐标
- 触摸下半屏：绑定为玩家 2 控制，`p2y` 设为触摸点 x 坐标
- 坐标经 `clampX()` 限制在 `[paddleW/2, width - paddleW/2]`

### ACTION_MOVE
- 遍历所有手指，按 pointerId 匹配玩家 1 或 2，更新球拍位置

### ACTION_UP / ACTION_POINTER_UP / ACTION_CANCEL
- 释放对应手指的绑定

## 5. 核心更新循环（update(dt)）

### 5.1 球的移动
```
ballX += vx * dt
ballY += vy * dt
```

### 5.2 左右墙反弹
- 球碰到左墙：`ballX = ballR`，`vx = |vx|`
- 球碰到右墙：`ballX = width - ballR`，`vx = -|vx|`

### 5.3 球拍碰撞
- **玩家1（顶部）**：球向上运动（`vy < 0`）且球底部触及球拍顶部区域，水平范围在球拍 ± ballR 内
  - 反弹：`vy = |vy|`
  - 水平速度调整：`vx = |vx| * 0.92 + (ballX - p1y) / (paddleW/2) * speed * 0.7`
  - 调用 `boostVelocity()` 加速
- **玩家2（底部）**：球向下运动（`vy > 0`）且球顶部触及球拍底部区域
  - 反弹：`vy = -|vy|`
  - 同样的水平速度调整逻辑

### 5.4 出界得分
- 球超出顶部（`ballY < -ballR*2`）：玩家 2 得分（`s2++`）
- 球超出底部（`ballY > height + ballR*2`）：玩家 1 得分（`s1++`）

### 5.5 boostVelocity()
- 每次碰撞后整体速度提升：`cur *= ACCEL`，上限 `MAX_SPEED`
- 保持速度方向不变，仅缩放大小

## 6. 胜负判定

- **得分后**：调用 `checkWin()`
- **胜利条件**：`s1 >= 5` 或 `s2 >= 5`
- **胜者判定**：`s1 > s2` 则玩家 1 获胜，否则玩家 2 获胜
- **结算**：`GameResult(score=0, won=p1Win, summary=描述)`
- **未到 5 分**：调用 `resetBall()` 重新发球

## 7. 边界与特殊规则

- ⚠️ **球拍碰撞区域**：球拍位于固定 y 坐标（`topY` / `bottomY`），球拍高度为 `paddleH`。碰撞检测使用球的边缘（`ballR`）而非中心。
- ⚠️ **速度递增**：每次球拍碰撞后球速乘以 1.07，上限 2400。初始速度 620 已经较快，避免拖拍拉锯。
- ⚠️ **水平速度计算**：碰撞时水平速度受击球位置影响——打在球拍边缘会给球更多水平分量。
- ⚠️ **球拍无上下移动**：球拍 y 坐标固定（`topY` / `bottomY`），玩家只能左右拖动。
- ⚠️ **出界判定宽松**：球超出屏幕 `2*ballR` 才算出界，避免视觉上球还在边缘就判定得分。

## 8. 渲染要求

- **背景**：深色（`0xFF141A2E`）
- **中线**：屏幕中间水平虚线（半透明白色）
- **球拍**：玩家1 蓝色（`0xFF4A6CF7`），玩家2 橙色（`0xFFFF7A45`），圆角矩形
- **球**：白色圆形
- **分数**：顶部显示"玩家1 X : Y 玩家2"
- **提示**：底部显示"先到 5 分获胜 · 各自在自己半屏拖动控制"
