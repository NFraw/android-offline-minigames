# 抢答算术（MathDuelView）

## 1. 游戏类型与视角

- **类型**：双人抢答（算术竞速）
- **视角**：上下分屏。玩家 1 在上半屏（整半屏旋转 180°，正对上方玩家），玩家 2 在下半屏。中间有分割线。题目在两个半屏内分别显示，各自正对自己方向的玩家。
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
companion object {
    const val WIN_SCORE = 5         // 先到此分获胜
    const val MODE_10 = 0           // 10 以内加减
    const val MODE_20 = 1           // 20 以内加减
    const val MODE_50 = 2           // 50 以内加减
    const val MODE_100 = 3          // 100 以内加减
    const val MODE_MIX100 = 4       // 100 以内四则
    const val MODE_MIX1000 = 5      // 1000 以内四则
    val MODE_NAMES = arrayOf(...)   // 各模式名称
    val MODE_DESC = arrayOf(...)    // 各模式描述
}

data class Key(val rect: RectF, val label: String, val action: Int)  // 键盘按键

var phase: Int         // 0=选模式，1=对战中
var mode: Int          // 当前难度模式（MODE_10..MODE_MIX1000）
val modeBtns: ArrayList<Pair<RectF, Int>>  // 难度选择按钮

// 题目
var a: Int             // 左操作数
var b: Int             // 右操作数
var op: Char           // 运算符（+ - × ÷）
var answer: Int        // 正确答案

// 得分
var s1: Int            // 玩家 1 得分
var s2: Int            // 玩家 2 得分

// 输入状态
var input1: String     // 玩家 1 当前输入
var input2: String     // 玩家 2 当前输入
var locked1: Boolean   // 玩家 1 本题是否已锁定（答错）
var locked2: Boolean   // 玩家 2 本题是否已锁定
var flash: Int         // 答对时的闪烁效果：-1=无，0=玩家1，1=玩家2
var flashTimer: Float  // 闪烁计时器
var over: Boolean      // 游戏是否已结束

val keys1: ArrayList<Key>  // 玩家 1 键盘
val keys2: ArrayList<Key>  // 玩家 2 键盘
```

## 3. 初始化逻辑（reset）

1. `phase = 0`（显示难度选择界面）。
2. `s1 = 0`，`s2 = 0`，`over = false`，`flash = -1`，`flashTimer = 0`。
3. `setScore(0)`，调用 `start()`。
4. 玩家选择难度后（`phase = 1`）调用 `newQuestion()` 生成第一题。

### 题目生成（newQuestion）
- 重置输入：`input1 = ""`，`input2 = ""`，`locked1 = false`，`locked2 = false`
- 按模式生成：
  - **加减模式**（MODE_10/20/50/100）：随机选 + 或 -，结果严格控制在范围内
  - **四则模式**（MODE_MIX100/MIX1000）：随机选 + - × ÷
    - 乘法：因子上限分别为 10/30，乘积不超过范围
    - 除法：`a = 商 × 除数`，保证整除且 ≤ 范围

### 最大输入位数（maxInput）
- MODE_10/20/50 → 2 位
- MODE_100/MIX100 → 3 位
- MODE_MIX1000 → 4 位

## 4. 输入处理

- **触发条件**：`ACTION_DOWN`，且 `!paused && !over`
- **难度选择阶段**（`phase == 0`）：点击模式按钮选择难度，进入对战
- **对战阶段**（`phase == 1`）：
  - 根据触摸 Y 坐标判断玩家：`y < height/2` → 玩家 1，否则 → 玩家 2
  - **玩家 1 触摸坐标变换**：上半屏旋转 180°，需反变换 `px = width - event.x`，`py = height/2 - event.y`
  - 命中测试键盘按键，调用 `onKey(tap, k)`：
    - **数字键**（0-9）：追加到输入字符串，受 `maxInput()` 长度限制
    - **删除键**（⌫）：删除最后一个字符
    - **提交键**（✓）：调用 `submit(tap)`

### 提交判定（submit）
- 输入转整数，与 `answer` 比较：
  - **答对**：当前玩家得分 +1，播放 EAT 音效，闪烁效果
    - 若达到 `WIN_SCORE`（5 分）→ 游戏结束
    - 否则生成新题
  - **答错**：当前玩家本题锁定（`locked`），播放 LOSE 音效，Toast 提示

## 5. 核心更新循环（update(dt)）

```
if over: return
if flashTimer > 0: flashTimer -= dt
invalidate()
```

仅用于驱动答对时的闪烁动画效果（`flashTimer` 从 0.8 递减）。

## 6. 胜负判定

- **胜利条件**：任一玩家得分达到 `WIN_SCORE`（5 分）
- **判定时机**：`submit` 中答对后立即检查
- **结算消息**：`"玩家 {N} 率先拿到 5 分获胜！"`

## 7. 边界与特殊规则

- **双视角显示**：玩家 1 的上半屏整体绕中心旋转 180°（`canvas.translate + rotate`），触摸坐标需做对应反变换。
- **题目持久显示**：题目展示后保持不变，直到有人提交正确答案才换新题。
- **答错锁定**：答错的玩家本题不能再答（`locked`），但不影响对方继续答题。下一题自动解锁。
- **输入位数限制**：按模式限制最大位数，防止乱输。
- **除法整除保证**：`a = q * b`，其中 `q` 为商、`b` 为除数，天然整除。
- **乘法范围控制**：`do-while` 循环确保 `a * b <= range`。
- **加减法范围控制**：加法 `a + b <= range`，减法 `a - b >= 1`。

## 8. 渲染要求

- 难度选择界面：半透明遮罩，6 个模式按钮（2 列 × 3 行），显示模式名称和描述。
- 对战阶段：上半屏玩家 1 色调（`0x334A6CF7` 蓝），下半屏玩家 2 色调（`0x33FF7A45` 橙）。
- 题目居中显示，字号 88f。答对时变绿（`0xFF3ED98A`）。
- 键盘：3 列 × 4 行（1-9, ⌫, 0, ✓），玩家 1 用蓝色描边，玩家 2 用橙色描边。
- 输入区域：显示当前输入或下划线占位符；锁定时显示红色"本题已锁定"。
- 得分：玩家 1 蓝色（`0xFF6C8BFF`），玩家 2 橙色（`0xFFFFA04A`）。
