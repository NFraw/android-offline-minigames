# 蛇蛇大作战（SnakeBattle）游戏逻辑文档

## 1. 游戏类型与视角

- **类型**：双人实时对战
- **视角约定**：玩家 1（绿色蛇）在屏幕上半区操作，玩家 2（橙色蛇）在下半区操作。屏幕中央有分隔线区分两个玩家的控制区域。
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
// 点坐标
private data class Pt(val x: Int, val y: Int)

// 方向键按钮
private data class Btn(val rect: RectF, val dx: Int, val dy: Int, val label: String)

// 蛇身（头部在 first，尾部在 last）
private val body1 = ArrayDeque<Pt>()   // 玩家1 蛇身
private val body2 = ArrayDeque<Pt>()   // 玩家2 蛇身

// 当前方向（本拍已提交）
private var dir1x = 1; private var dir1y = 0   // 玩家1 方向
private var dir2x = -1; private var dir2y = 0   // 玩家2 方向

// 待执行方向（缓冲一拍，防止快速连按 180° 掉头）
private var nd1x = 1; private var nd1y = 0
private var nd2x = -1; private var nd2y = 0

private val foods = ArrayDeque<Pt>()   // 食物列表
private var alive1 = true              // 玩家1 存活
private var alive2 = true              // 玩家2 存活
private var over = false               // 游戏是否结束
private var started1 = false           // 玩家1 是否已开始移动
private var started2 = false           // 玩家2 是否已开始移动
private var acc = 0f                   // 时间累加器
private var speed = 5.5f               // 移动速度（每秒步数）

// 棋盘参数
private var cols = 16                  // 列数（最大 16）
private var rows = 24                  // 行数
private var cell = 0f                  // 单元格像素大小
private var boardLeft = 0f             // 棋盘左边界
private var boardTop = 0f              // 棋盘上边界

// 触控状态（支持双人同时滑动）
private val touchState = HashMap<Int, FloatArray>()  // pointerId -> [zone, downX, downY]

// 方向键（上/下半屏各一套十字键）
private val pad1 = ArrayList<Btn>()    // 玩家1 方向键
private val pad2 = ArrayList<Btn>()    // 玩家2 方向键
```

## 3. 初始化逻辑（reset）

1. 清空两条蛇的蛇身和食物列表
2. 玩家1 蛇初始位置：`(cols/4, rows/2)` 处 3 节，方向向右（1,0）
3. 玩家2 蛇初始位置：`(cols*3/4, rows/2)` 处 3 节，方向向左（-1,0）
4. 重置所有状态标志：`alive1/alive2 = true`，`over = false`，`started1/started2 = false`
5. 重置速度为 5.5f，累加器为 0f
6. 生成 3 个随机食物（避开蛇身和已有食物）
7. 调用 `start()` 启动帧循环

## 4. 输入处理

### 4.1 方向键输入（ACTION_DOWN / ACTION_POINTER_DOWN）
- 检测触摸点是否命中 `pad1` 或 `pad2` 的任意按钮
- 命中则调用 `setDir(zone, dx, dy)` 设置对应玩家的待执行方向
- 播放 MOVE 音效

### 4.2 滑动输入（ACTION_MOVE）
- 对每根手指计算与上次位置的偏移 `(dx, dy)`
- 当 `|dx| > 44` 或 `|dy| > 44` 像素时判定为滑动
- 水平偏移更大则设左右方向，否则设上下方向
- 按半屏区域（zone）分配给对应玩家

### 4.3 防 180° 掉头
- `setDir()` 中检查新方向是否与当前方向或待执行方向相反
- 相反则拒绝（避免蛇头立即撞自己身体）

## 5. 核心更新循环（update(dt)）

### 5.1 时间累加
```
acc += dt * speed    // speed = 5.5 步/秒
while (acc >= 1f):
    acc -= 1f
    step()           // 执行一步
```

### 5.2 step() 逻辑
1. **提交方向**：将待执行方向 `nd1/nd2` 赋值给当前方向 `dir1/dir2`
2. **计算新头位置**：对存活且已开始移动的蛇，计算 `head + dir`
3. **撞墙检测**：新头超出 `[0, cols) × [0, rows)` 则该蛇死亡
4. **头撞头**：两条蛇新头位置相同，两条都死
5. **撞对方身体**：仅在双方都存活时检测（死蛇身体不再阻挡幸存者）
6. **撞自己身体**：新头与自身身体任意一节重合则死
7. **移动与吃食**：
   - 新头加入蛇身头部
   - 如果新头位置有食物：移除食物、播放 EAT 音效、生成新食物
   - 否则移除尾部一节（保持长度不变）
8. **终局判定**：两条蛇都死亡时游戏结束，按身体长度比较胜负

## 6. 胜负判定

- **继续条件**：一条蛇死后另一条继续玩
- **结束条件**：两条蛇都死亡
- **胜负规则**：比较两条蛇身体长度（`body1.size` vs `body2.size`），长者获胜
- **平局**：两蛇长度相等
- **结算**：`GameResult(score=0, won=长度不等, summary=描述文案)`

## 7. 边界与特殊规则

- ⚠️ **方向缓冲**：待执行方向与当前方向分离（`nd` vs `dir`），每步才提交一次。这防止了两个方向键快速连按时直接 180° 掉头撞自己。
- ⚠️ **死蛇不阻挡**：一条蛇死后，其身体不再作为碰撞体，幸存者可以穿过死亡蛇的身体。
- ⚠️ **头撞头同时死亡**：两条蛇同时向同一格移动时双双死亡。
- ⚠️ **已开始移动判断**：`started1/started2` 为 false 时该蛇不移动（等待玩家首次操作方向键）。
- ⚠️ **食物生成**：随机选择不在蛇身和已有食物上的空闲格子。
- ⚠️ **速度固定**：`speed = 5.5f` 步/秒，不随游戏进程加速。

## 8. 渲染要求

- **背景**：深色（`0xFF141A2E`）
- **棋盘**：居中绘制，带网格线（半透明白色），外围有填充墙（`0xFF3A4668`）+ 亮色描边（`0xFFE8EAF0`），撞墙即死需清晰可见
- **分区线**：屏幕中间水平线分隔两玩家区域，上半区有微弱高亮
- **蛇身**：玩家1 绿色（`0xFF3ED98A` / `0xFF7BEBAD`），玩家2 橙色（`0xFFF5A54A` / `0xFFF8C98A`），每节为圆角矩形，头部颜色更亮
- **食物**：红色圆形（`0xFFFF5252`）
- **方向键**：上下半屏各一套十字键（`▲◀▶▼`），带圆角背景和描边
- **提示文字**：未开始移动时显示"（滑动也行）"
