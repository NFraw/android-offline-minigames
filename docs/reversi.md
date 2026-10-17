# 黑白棋（Reversi / Othello）游戏逻辑文档

本文档涵盖两个版本：**ReversiView**（双人对战）和 **ReversiAiView**（人机对战）。

## 1. 游戏类型与视角

### ReversiView（双人）
- **类型**：双人回合制
- **视角约定**：共享 8×8 棋盘，黑方先手，轮流落子
- **屏幕方向**：竖屏

### ReversiAiView（人机）
- **类型**：人机回合制
- **视角约定**：玩家执黑先手，AI 执白后手
- **屏幕方向**：竖屏

## 2. 数据模型

两个版本共享相同的核心数据结构：

```kotlin
private const val N = 8           // 棋盘尺寸
private const val EMPTY = 0      // 空位
private const val BLACK = 1      // 黑方
private const val WHITE = 2      // 白方

private val board = IntArray(N * N)   // 棋盘状态（一维数组，row-major）
private var curPlayer = BLACK         // 当前落子方
private var over = false
private var passCount = 0             // 连续跳过计数（双方都无子可下时终局）

// 布局参数
private var cell = 0f
private var boardLeft = 0f
private var boardTop = 0f
```

### ReversiAiView 额外状态
```kotlin
private var aiThinking = false        // AI 是否正在思考

// 位置权重表（AI 决策用）
private val WEIGHT = arrayOf(
    intArrayOf(120, -20, 20, 5, 5, 20, -20, 120),
    intArrayOf(-20, -40, -5, -5, -5, -5, -40, -20),
    intArrayOf(20, -5, 15, 3, 3, 15, -5, 20),
    intArrayOf(5, -5, 3, 3, 3, 3, -5, 5),
    intArrayOf(5, -5, 3, 3, 3, 3, -5, 5),
    intArrayOf(20, -5, 15, 3, 3, 15, -5, 20),
    intArrayOf(-20, -40, -5, -5, -5, -5, -40, -20),
    intArrayOf(120, -20, 20, 5, 5, 20, -20, 120)
)
```

### ReversiView 额外状态
```kotlin
private val validMoves = ArrayList<Int>()   // 当前合法落子位置（用于渲染提示）
```

### 8 个方向常量
```kotlin
private val dirs = listOf(
    Pair(-1, -1), Pair(0, -1), Pair(1, -1),
    Pair(-1, 0), Pair(1, 0),
    Pair(-1, 1), Pair(0, 1), Pair(1, 1)
)
```

## 3. 初始化逻辑（reset）

两个版本相同：

1. 清空棋盘
2. 设置初始 4 子（中心 2×2 区域）：
   - `(3,3)` = WHITE，`(3,4)` = BLACK
   - `(4,3)` = BLACK，`(4,4)` = WHITE
3. `curPlayer = BLACK`（黑方先手）
4. `over = false`，`passCount = 0`
5. ReversiAiView 额外：`aiThinking = false`
6. 调用 `start()`

## 4. 输入处理

### ReversiView（双人）
- 仅处理 `ACTION_DOWN`，且 `over` 或 `paused` 时忽略
- 转换坐标为 `(r, c)`，越界忽略
- 调用 `isValid(idx, curPlayer)` 检查合法性
- 合法则落子：`board[idx] = curPlayer`，`flip(idx, curPlayer)` 翻转棋子
- 播放 CLICK 音效，调用 `nextTurn()`

### ReversiAiView（人机）
- 仅处理 `ACTION_DOWN`，且 `over`、`paused`、`aiThinking` 或 `curPlayer != BLACK` 时忽略
- 仅允许玩家在自己回合（BLACK）操作
- 落子后立即调用 `aiTurn()` 启动 AI

## 5. 核心更新循环（update(dt)）

无逻辑 —— 本游戏为回合制。

### AI 思考流程（ReversiAiView）

`aiTurn()` 在独立线程中执行：

1. 设置 `aiThinking = true`
2. 遍历所有格子，找合法落子位置
3. 对每个合法位置计算权重分数：`WEIGHT[i/N][i%N]`
4. 选择权重最高的位置作为 `bestMove()`
5. 通过 `post {}` 回到主线程：
   - 落子：`board[best] = WHITE`，`flip(best, WHITE)`
   - 播放 EAT 音效
   - 调用 `nextTurn()`

## 6. 核心算法

### 6.1 isValid(idx, player) —— 合法性检查

1. 格子必须为空
2. 从该格子出发，沿 8 个方向搜索
3. 每个方向：连续遇到对方棋子后遇到己方棋子 → 合法
4. 任一方向满足即返回 true

### 6.2 flip(idx, player) —— 翻转棋子

1. 从落子位置出发，沿 8 个方向搜索
2. 每个方向：收集连续的对方棋子
3. 如果该方向末端是己方棋子：将收集到的对方棋子全部翻转

### 6.3 nextTurn() —— 换手与终局

1. 检查对方是否有合法落子：
   - 有 → 切换到对方回合，`passCount = 0`
2. 对方无子可下，检查己方是否有合法落子：
   - 有 → 己方继续（对方跳过），`passCount++`
3. 双方都无子可下 → 终局

## 7. 胜负判定

- **终局条件**：双方都无合法落子位置
- **胜负规则**：数棋盘上黑子和白子数量，多者获胜
- **结算**：
  - 黑多 → "黑方获胜！"（ReversiView）/ "你赢了！"（ReversiAiView）
  - 白多 → "白方获胜！" / "电脑获胜！"
  - 相等 → "平局！"
- **音效**：黑多播放 WIN，否则播放 LOSE

## 8. 边界与特殊规则

- ⚠️ **初始布局**：中心 2×2 区域对角线放置，WHITE 在 (3,3) 和 (4,4)，BLACK 在 (3,4) 和 (4,3)。
- ⚠️ **跳过机制**：一方无合法落子时自动跳过，不终局。只有双方都无子可下才终局。
- ⚠️ **AI 权重策略**：角落（120）权重最高，角落旁的边（-20/-40）为负权重，中心区域权重较低。AI 选择权重最高的合法位置。
- ⚠️ **AI 线程安全**：AI 在后台线程计算，通过 `post {}` 回主线程更新 UI。`aiThinking` 标志防止玩家在 AI 思考时操作。
- ⚠️ **翻转方向**：8 个方向包括斜向，翻转必须两端都是己方棋子才生效。
- ⚠️ **ReversiView 提示**：每次绘制时重新计算 `validMoves`，在合法位置显示半透明白色小圆点。

## 9. 渲染要求

### 共同渲染
- **棋盘**：绿色底（`0xFF2E7D4F`），网格线（`0xFF1E5C3A`）
- **黑子**：深色圆形（`0xFF141A2E`）
- **白子**：白色圆形（`0xFFF5F6FA`），带浅灰描边（`0xFFB9C0CE`）
- **回合提示**：显示当前回合方和双方子数

### ReversiView 特有
- 合法落子位置显示半透明白色小圆点（`0x66FFFFFF`）

### ReversiAiView 特有
- AI 思考时显示"🤖 电脑思考中…"
- 仅在玩家回合（BLACK）显示合法落子提示
