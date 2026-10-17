# 西蒙记忆（SimonView）

## 1. 游戏类型与视角

- **类型**：单人记忆（Simon Says 风格）
- **视角**：俯视，2×2 四色方阵
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
val sequence = ArrayList<Int>   // 颜色序列，值为 0~3（对应四个象限）
var inputIdx: Int               // 玩家当前输入位置
var showIdx: Int                // 展示序列时的当前索引
var showTimer: Float            // 展示计时器（控制亮/灭时长）
var showLit: Boolean            // true = 当前亮灯阶段，false = 熄灭间隔
var showing: Boolean            // true = 正在展示序列，false = 等待玩家输入
var level: Int                  // 当前关卡（从 1 开始）
var over: Boolean               // 游戏结束标志

// 点击反馈
var tapFlash: Float             // 点击反馈剩余时长，-1 表示无反馈
var tapQuad: Int                // 被点击的象限（0~3）

// 布局
var quadR: Float                // 色块方阵半边长 = min(w, h) * 0.42

// 颜色定义
val colors = [红 0xFFFF5252, 绿 0xFF3ED98A, 黄 0xFFF5D547, 蓝 0xFF37C2F0]
val dimColors = [暗红 0xFF7A2B2B, 暗绿 0xFF1E5C3A, 暗黄 0xFF7A6A24, 暗蓝 0xFF1E4568]
```

### 象限编号

```
  0 (左上)  |  1 (右上)
  ----------+----------
  2 (左下)  |  3 (右下)
```

判定逻辑：`dx > 0 ? (dy > 0 ? 3 : 1) : (dy > 0 ? 2 : 0)`

## 3. 初始化逻辑（reset）

```kotlin
sequence.clear()
sequence.add((0..3).random())   // 初始序列：1 个随机颜色
inputIdx = 0
showIdx = 0
showLit = true
showTimer = 0.5f                // 首个色块延迟 0.5 秒后亮起
showing = true                  // 进入展示模式
level = 1
over = false
tapFlash = -1f                  // 无点击反馈
setScore(0)
start()
```

## 4. 输入处理

- **触发条件**：`ACTION_DOWN`，且 `!over && !paused && !showing`（展示期间禁止输入）
- **象限判定**：基于触摸点相对屏幕中心的 `dx/dy`，用正方形边界（`abs(dx) > quadR`）判定是否在色块范围内
- **正确输入**：
  1. `inputIdx++`
  2. 若 `inputIdx >= sequence.size`（本关完成）：`level++`，向序列追加一个随机颜色，重新进入展示模式
- **错误输入**：立即结束游戏
- **点击反馈**：`tapFlash = 0.25f`，`tapQuad = q`，被按下的色块亮起 0.25 秒

## 5. 核心更新循环（update(dt)）

每帧执行：

1. **点击反馈衰减**：`tapFlash -= dt`（当 `tapFlash > 0` 时）
2. **展示序列状态机**（当 `showing == true` 时）：
   - `showTimer -= dt`
   - 归零时切换状态：
     - **亮灯 → 熄灭**：`showLit = false`
       - 若当前色块与前一个相同：熄灭时长 `0.55f` 秒（更长，便于区分连续同色）
       - 否则：熄灭时长 `0.36f` 秒
     - **熄灭 → 下一个亮灯**：`showIdx++`
       - 若已展示完所有色块：`showing = false`，进入等待输入
       - 否则：`showLit = true`，亮灯时长 `0.3f` 秒
3. 每帧调用 `invalidate()` 触发重绘

### 展示时序示例（序列 [2, 0, 0, 3]）

```
亮2 (0.3s) → 灭 (0.36s) → 亮0 (0.3s) → 灭 (0.55s, 同色加长) → 亮0 (0.3s) → 灭 (0.36s) → 亮3 (0.3s) → 灭 → 等待输入
```

## 6. 胜负判定

- **胜利**：无明确胜利条件，关卡无限递增
- **失败**：玩家按下错误颜色，消息 `"记错啦！到达第 {level} 关"`
- **得分**：`level * 10`

## 7. 边界与特殊规则

- **连续同色处理**：当序列中相邻两个颜色相同时，中间的熄灭间隔延长到 `0.55f` 秒（正常为 `0.36f`），让玩家能分辨出是两次独立的亮灯。
- **正方形判定区域**：色块用正方形边界判定（`abs(dx) > quadR || abs(dy) > quadR`），与绘制一致。这意味着四角区域都能被点到。
- **展示期间禁止输入**：`showing == true` 时触摸无效。
- **level 从 1 开始**：初始序列长度为 1，每过一关长度 +1。
- **点击反馈独立于展示**：`tapFlash` 和 `showLit` 是两套独立的亮灯机制，互不干扰。

## 8. 渲染要求

- 四个色块为 2×2 方阵，居中，半边长 `min(w, h) * 0.42`。
- 色块圆角 `dp(14)`。
- 亮灯用 `colors[q]`，暗灯用 `dimColors[q]`。
- 中心圆（半径 `quadR * 0.42`）显示当前关卡文字 `"第 {level} 关"`。
- 点击反馈（`tapFlash > 0`）时被按下的色块也亮起。
