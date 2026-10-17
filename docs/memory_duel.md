# 记忆对决（MemoryDuelView）

## 1. 游戏类型与视角

- **类型**：双人休闲（翻牌配对 / Memory Match）
- **视角**：横屏。棋盘居中，玩家 1 面板在左侧，玩家 2 面板在右侧。玩家 2 的面板旋转 180° 正对右侧玩家。
- **屏幕方向**：横屏（`landscape = true`）

## 2. 数据模型

```kotlin
companion object {
    val EMOJIS = listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼",
                        "🐨","🐯","🦁","🐮","🐷","🐸","🐵","🐔")  // 16 种 emoji
    val COLORS = intArrayOf(...)  // 9 种颜色
    val LEVELS = intArrayOf(4, 8, 10, 16)  // 4 关的棋盘尺寸
}

var cards: IntArray          // 卡片图案编号（0..N-1），配对用
var matched: BooleanArray    // 卡片是否已配对成功
var size: Int                // 当前棋盘边长（4/8/10/16）
var flipA: Int               // 第一张翻开的卡片索引，-1=未翻
var flipB: Int               // 第二张翻开的卡片索引，-1=未翻
var lockTimer: Float         // 配错锁定计时器（0.7 秒后翻回并换手）
var turn: Int                // 当前回合：0=玩家1，1=玩家2
var s1: Int                  // 玩家 1 得分（配对成功次数）
var s2: Int                  // 玩家 2 得分
var score: Int               // 总分（每次配对 +10，用于 Activity 显示）
var level: Int               // 当前关卡（0..3）
var finished: Boolean        // 是否全部通关

// 布局相关
var lastW: Int / lastH: Int  // 上次布局尺寸（用于 recomputeLayout）
var cell: Float              // 卡片边长
var boardLeft: Float / boardTop: Float  // 棋盘左上角
var panelW: Float            // 侧边面板宽度
```

## 3. 初始化逻辑（reset）

1. `level = 0`，`size = LEVELS[0]`（4×4）。
2. `s1 = 0`，`s2 = 0`，`score = 0`。
3. 调用 `recomputeLayout()` 计算布局。
4. 调用 `deal()` 发牌：
   - 生成 `size*size/2` 对配对编号（0,0,1,1,...）
   - 随机打乱填入 `cards`
   - `matched` 全部 false
   - `flipA = -1`，`flipB = -1`，`lockTimer = 0`，`turn = 0`，`finished = false`
5. `setScore(0)`，调用 `start()`。

### 关卡晋级（levelCleared）
- 当前关非最后一关：`level++`，重新计算布局，重新发牌，显示 Toast 提示关卡信息
- 当前关是最后一关（`level == LEVELS.lastIndex`）：全部通关，比较 `s1` 和 `s2` 判定胜负

## 4. 输入处理

- **触发条件**：`ACTION_DOWN`，且 `lockTimer <= 0` 且 `!finished`
- **坐标转换**：触摸坐标 → 列 `c`、行 `r`，越界检查
- **已匹配或已翻开的卡片**：忽略点击
- **第一张翻开**（`flipA < 0`）：记录 `flipA = idx`，播放 CLICK 音效
- **第二张翻开**（`flipB = idx`）：
  - **配对成功**（`cards[flipA] == cards[flipB]`）：
    - 标记 `matched`，当前玩家得分 +1，总分 +10
    - 播放 EAT 音效，重置 flipA/flipB
    - 若全部配对完成（`matched.all { it }`）→ 调用 `levelCleared()`
  - **配对失败**：
    - 设置 `lockTimer = 0.7` 秒（锁定期间不能点击）

## 5. 核心更新循环（update(dt)）

```
if lockTimer > 0:
    lockTimer -= dt
    if lockTimer <= 0:
        flipA = -1, flipB = -1    // 翻回
        turn = 1 - turn            // 换手
    invalidate()
```

配对成功时不触发 lockTimer，当前玩家继续翻牌。配错时 lockTimer 倒计时结束后翻回卡片并换手。

## 6. 胜负判定

### 关卡内
- 配对成功：当前玩家得分 +1（`s1++` 或 `s2++`），总分 `score += 10`
- 配对成功后继续翻牌（不换手）
- 配对失败：换手

### 全部通关后（levelCleared）
- 比较 `s1` 和 `s2`：
  - `s1 > s2`：玩家 1 获胜
  - `s1 < s2`：玩家 2 获胜
  - `s1 == s2`：平局
- 结算消息：`"全部通关！玩家 1 {s1} : {s2} 玩家 2{verdict}"`

## 7. 边界与特殊规则

- **卡片图案唯一性**：图案 = emoji（16 种）× 颜色（9 种），16 与 9 互质，共 144 种不同视觉组合。保证 16×16 的 128 对也互不相同。
- **关卡递进**：4×4（8 对）→ 8×8（32 对）→ 10×10（50 对）→ 16×16（128 对）。
- **分数累计**：`s1`/`s2` 跨关卡累计，`score` 也跨关卡累计。
- **发牌时保留局外状态**：`deal()` 只重置 `cards`/`matched`/`flipA`/`flipB`/`lockTimer`/`turn`/`finished`，不清零 `level`/`score`/`s1`/`s2`。
- **横屏布局**：`panelW = dp(100)`，棋盘可用宽度 = `width - panelW * 2`，棋盘边长取 `min(可用宽, 高) * 0.94`。
- **卡片尺寸**：`cell = side / size`，每格有 1.5px 间距。

## 8. 游戏画面渲染

- 左右两侧玩家面板：显示玩家名、分数、回合指示块（轮到时高亮蓝色/橙色）。
- 棋盘中央：未翻开的卡片为深色圆角矩形（`faceDownPaint`，`0xFF2A3557`），中心显示 "?"。
- 翻开/已匹配的卡片：浅色圆角矩形（`faceUpPaint`，`0xFFE8EAF0`），显示颜色圆 + emoji。
- 顶部中央显示关卡信息。
