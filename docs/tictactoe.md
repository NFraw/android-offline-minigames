# 井字棋（TicTacToe）游戏逻辑文档

## 1. 游戏类型与视角

- **类型**：双人回合制
- **视角约定**：双方共享同一 3×3 棋盘，轮流落子。X 方（玩家1）用 ✕ 标记，O 方（玩家2）用 ○ 标记。
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
private const val N = 3           // 棋盘尺寸
private const val EMPTY = 0      // 空位
private const val X = 1          // 玩家1
private const val O = 2          // 玩家2

private val board = IntArray(N * N)   // 棋盘状态（一维数组，row-major）
private var curPlayer = X             // 当前落子方
private var over = false

// 布局参数
private var cell = 0f            // 单元格像素大小
private var boardLeft = 0f       // 棋盘左边界
private var boardTop = 0f        // 棋盘上边界
```

## 3. 初始化逻辑（reset）

1. 清空棋盘：所有格子设为 `EMPTY`
2. **交换先手**：`curPlayer = if (curPlayer == X) O else X`（每局交替先手）
3. `over = false`
4. 调用 `start()`

## 4. 输入处理

- 仅处理 `ACTION_DOWN`，且 `over` 或 `paused` 时忽略
- 将触摸坐标转换为棋盘格子 `(c, r)`
- 越界检查：`r !in 0..2` 或 `c !in 0..2` 则忽略
- 已有棋子的格子忽略
- 落子：`board[idx] = curPlayer`，播放 CLICK 音效
- 落子后立即检查胜负

## 5. 核心更新循环（update(dt)）

无逻辑 —— 本游戏为回合制，不依赖帧更新。

## 6. 胜负判定

落子后按以下顺序判定：

1. **胜利检查**：`checkWin(curPlayer)` —— 检查 8 条连线（3 行 + 3 列 + 2 对角线）是否全为当前玩家
   - 胜利：`gameOver(GameResult(0, true, "X/O 方三子连线获胜！"))`
2. **平局检查**：`board.none { it == EMPTY }` —— 棋盘已满
   - 平局：`gameOver(GameResult(0, false, "平局！"))`
3. **换手**：`curPlayer = if (curPlayer == X) O else X`

### 8 条胜利线
```
行: [0,1,2], [3,4,5], [6,7,8]
列: [0,3,6], [1,4,7], [2,5,8]
对角: [0,4,8], [2,4,6]
```

## 7. 边界与特殊规则

- ⚠️ **先手交换**：`reset()` 时 `curPlayer` 取反，保证每局先手交替。首次游戏 X 先手。
- ⚠️ **点击即落子**：不需要确认步骤，点击空位立即落子并判定。
- ⚠️ **平局判定**：棋盘满且无人三子连线则平局，`won = false`。
- ⚠️ **update 为空**：所有逻辑在 `onTouch` 中同步完成。

## 8. 渲染要求

- **背景**：深色（`0xFF141A2E`）
- **棋盘格**：3×3 圆角矩形，深色底（`0xFF1E2740`），间距 4px
- **X 标记**：橙色（`0xFFFF7A45`），用两条对角线绘制
- **O 标记**：蓝色（`0xFF6C8BFF`），圆形绘制
- **回合提示**：顶部显示"✕ 玩家1 落子"或"○ 玩家2 落子"
