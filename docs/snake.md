# 贪吃蛇（snake）

## 1. 游戏类型与视角

| 项目 | 值 |
|------|---|
| 类型 | 单人 |
| 视角约定 | 无方向 |
| 屏幕方向 | 竖屏 |

## 2. 数据模型

```kotlin
data class Pt(val x: Int, val y: Int)

val body = ArrayDeque<Pt>()   // 蛇身，头部在 first()
var dirX: Int = 1             // 当前方向 X
var dirY: Int = 0             // 当前方向 Y
var nextDirX: Int = 1         // 待执行方向（缓冲一拍）
var nextDirY: Int = 0
var food: Pt = Pt(0, 0)       // 食物位置
var score: Int = 0
var speed: Float = 5f         // 每秒移动格数
var acc: Float = 0f            // 移动累加器
var alive: Boolean = true
var started: Boolean = false   // 玩家首次操作前为 false
var cols: Int = 16             // 棋盘列数
var rows: Int = 24             // 棋盘行数
var cell: Float = 0f           // 格子像素尺寸
```

## 3. 初始化逻辑

- 蛇身初始 3 节，位于棋盘中央（`cols/2, rows/2`），向右排列
- 方向 `dirX=1, dirY=0`（向右）
- `speed = 5f`，`score = 0`，`alive = true`，`started = false`
- 随机在空位生成一个食物

## 4. 输入处理

- **方向键**：屏幕下方十字按钮（▲▼◀▶），`ACTION_DOWN` 时检测点击区域
- **滑动**：`GestureDetector.onFling` 识别滑动方向
- 防 180° 掉头：与当前方向或待执行方向相反均不接受（`nextDirX/nextDirY` 也检查）
- 任意有效输入将 `started = true`

## 5. 核心更新循环

```
每帧 update(dt):
  若 !alive 或 !started → return
  acc += dt * speed
  while acc >= 1:
    acc -= 1
    step()

step():
  dirX = nextDirX; dirY = nextDirY
  新头 = (head.x + dirX, head.y + dirY)
  若新头越界 → die("撞到墙了")
  若新头撞到 body → die("咬到自己了")
  body.addFirst(新头)
  若新头 == food:
    score += 10; setScore(score)
    每 50 分且 speed < 13 → speed += 0.6
    spawnFood()
  否则:
    body.removeLast()
```

## 6. 胜负判定

- **失败**：撞墙（越界）或咬到自己 → `gameOver(score, false, reason)`
- **胜利**：蛇身填满整个棋盘（无空位生成食物）→ `gameOver(score, true, "你赢了！")`

## 7. 边界与特殊规则

1. **方向缓冲**：使用 `nextDirX/nextDirY` 缓冲一拍，防止快速连按时直接 180° 掉头撞自己
2. **速度递增**：每吃 50 分加速 0.6，上限 13 格/秒
3. **食物生成**：在所有非蛇身空位中随机选择；若无空位则判胜利
4. **格子数**：固定 16×24，格子大小取 `min(width/16, availHeight/24)` 适配屏幕

## 8. 渲染要求

- 棋盘有可见边界墙（深色填充 + 亮色描边）
- 蛇头与蛇身颜色不同（头亮蓝 `#6C8BFF`，身蓝 `#4A6CF7`）
- 蛇头绘制眼睛（两个白色圆点，方向跟随 `dirX/dirY`）
- 食物为红色圆形
- 未开始时显示提示文字："👆 滑动屏幕或点击下方方向键开始"
- 方向键区域在棋盘下方，十字布局
