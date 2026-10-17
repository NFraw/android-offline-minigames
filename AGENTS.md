# 新版开发约定（优先于下文旧版基线说明）

- 每款游戏方向固定，以 GameRegistry.landscape 为唯一配置来源：飞行棋、斗兽棋、记忆对决、切水果固定横屏，其余固定竖屏；大厅可随设备旋转。每次切换同步安卓宿主和 Godot 窗口，画布保持 canvas_items + expand，旋转不重置对局。Android 清单保留 appCategory=game，使 Android 16 大屏遵守游戏方向。

- 大厅滚动：卡片/嵌套容器和按钮需MOUSE_FILTER_PASS，文字IGNORE，按钮抬手触发；从卡片或按钮拖动均应滚动且不误启动。勿将关卡深色背景直接用于飞机大战大厅卡片。

- 记忆翻牌和记忆对决最高8×8，仅保留4×4、6×6、8×8；动物模式使用共用精细图片图集，不改回几何手绘脸。

- 适龄交互（用户2026-10-16要求）：面向小学1—6年级，各游戏保留独有主题；大字号/清晰图标、短句三页图解，双人准备窗先展示规则；棋盘尽量居中。玩法以用户最新要求为准：白块误点判负，暗棋炮隔一子可远吃明子，冰壶同向且限制投壶范围。

- 用户验收流程：开发完成只做简单基础检查，通过后主动将最新版安装到电脑模拟器并打开，交给用户实际试玩；用户模拟器测完再上手机。不要未经要求扩大测试范围或反复消耗大量 token。

- 本目录是独立新版项目；禁止修改 `D:\03Project\安卓离线小游戏` 原仓库。
- 安卓优先、纯离线；29 款游戏、大厅和设置统一使用 Godot 4.7.2 标准版 + GDScript。
- 应用 ID：`com.minigames.collection.next`；namespace 保留 `com.minigames.collection`。
- `godot/project.godot` 为引擎工程入口；`scripts/` 提供环境检查、编辑器、打包及安装命令。
- 常用验证：`powershell -ExecutionPolicy Bypass -File scripts/Check-Environment.ps1`；`powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Test`。
- Godot 运行时版本必须与编辑器、安卓模板版本一致；兼容渲染器为默认选项。
- 新游戏是同一 Godot 工程内的独立场景；不得每款游戏引入一份引擎。
- 引擎验证场景不是正式游戏，不修改权威游戏收录数量。新正式游戏仍需同步游戏状态管理文档。
- 不修改 GBK JVM 设置。Godot 源码使用 UTF-8。
- 29 款正式入口全部走 Godot；旧 Kotlin View 保留作参考，禁止将正式入口改回 Canvas。手机默认仅打 arm64，模拟器打 x86_64。所有正式启动统一经过 GameLauncher。
- GameContent 定义引擎游戏契约；GameShell 管理通用生命周期。跨进程设置和成绩只能经过 GamePlatformProvider，中途存档尚未实现。
- 引擎规则测试：powershell -ExecutionPolicy Bypass -File scripts/Test-Godot.ps1。新增游戏须补充对应纯逻辑测试。
- 引擎启动/退出/返回大厅/后台恢复必须用模拟器或真机验证。

---
# 以下为旧版 Canvas 基线约定（仅适用于保留的旧源码）

# AGENTS.md — 迷你游戏合集

## 项目概述

纯离线安卓小游戏合集（29 款已收录）。纯 Kotlin + View/Canvas 自绘，零第三方引擎、零网络依赖。单 APK 约 3MB。minSdk 33（Android 13），targetSdk 36。

**技术栈**：AGP 8.13.2 + Kotlin 2.1.21 + Gradle 8.14.3。无 Jetpack Compose —— 全部游戏均为继承 `GameView` 的自定义 `View`，在 `Canvas` 上绘制。

## 构建命令

```bash
# 正式版 APK（推荐，使用项目内置 Gradle）
.tools\gradle-8.14.3\bin\gradle.bat :app:assembleRelease

# 或通过 wrapper
gradlew.bat :app:assembleRelease

# 调试版 APK
.tools\gradle-8.14.3\bin\gradle.bat :app:assembleDebug

# 单元测试
.tools\gradle-8.14.3\bin\gradle.bat :app:test

# 仪器化测试（需要模拟器/真机）
.tools\gradle-8.14.3\bin\gradle.bat :app:connectedAndroidTest
```

**APK 输出**：`app/build/outputs/apk/release/app-release.apk`（同时复制到 `releases/迷你游戏合集-vX.Y.Z.apk`）。

**签名**：`release.keystore` 位于仓库根目录（alias `minigames`，密码 `minigames123`）。

## 关键路径编码问题

项目路径包含中文字符。`gradle.properties` 强制 JVM 和 Kotlin daemon 使用 `file.encoding=GBK` —— **不要改成 UTF-8**。Gradle 测试 worker 以 GBK 编码写 `@argfile`；若改为 UTF-8，中文源码路径会导致测试时 `ClassNotFoundException`。

## 架构

```
app/src/main/java/com/minigames/collection/
├── MainActivity.kt          # 主菜单（分类筛选 + 游戏宫格）
├── GameActivity.kt          # 统一游戏容器（悬浮控件、暂停、重开、结算弹窗）
├── SettingsActivity.kt      # 设置页
├── common/
│   ├── GameView.kt          # 基类：Choreographer 帧循环、暂停/恢复、触摸分发、得分/结束回调
│   ├── GameSpec.kt          # 游戏注册表（GameRegistry.games 列表）—— 新增游戏只需加一条
│   ├── BoardArt.kt          # 桌游美术工具箱（木板/棋子/卡片/胶囊/状态条 + Palette 统一色板）
│   ├── GameResult.kt        # （在 GameView.kt 内）游戏结果数据类
│   ├── ScoreStore.kt        # SharedPreferences 最高分（scores.xml）
│   ├── SettingsStore.kt     # 设置持久化
│   ├── SoundManager.kt      # 内置音效（SoundPool, res/raw/*.wav）
│   ├── VoiceManager.kt      # 中文语音播报
│   ├── HapticManager.kt     # 震动反馈
│   ├── DualConsentView.kt   # 双人游戏同意页（分屏，双方都点"开始"才开局）
│   ├── SwipeHelper.kt       # 滑动手势识别
│   └── Ui.kt                # 共享 UI 工具（圆角背景、对话框样式）
└── games/                   # 每款游戏一个包，View 均继承 GameView
    ├── snake/  game2048/  tetris/  whack/  memory/  mines/
    ├── gomoku/ snakebattle/ pong/ tictactoe/ reversi/ reaction/
    ├── xiangqi/ darkchess/ animal/ weiqi/
    ├── piano/ sliding/ fruit/ bounce/ breakout/ plane/ simon/
    ├── pool/ curling/ math/ flight/ memory/ (MemoryDuelView)
    └── reaction/ (Reaction4View)
```

## 新增游戏步骤

1. 在 `app/src/main/java/com/minigames/collection/games/<id>/` 下创建 `*View.kt`，继承 `GameView`。
2. 实现 `update(dt: Float)`、`drawGame(canvas: Canvas)`、`reset()`，可选实现 `onLayout(w, h)`。
3. 在 `common/GameSpec.kt` 的 `GameRegistry.games` 中添加一条 `GameSpec(...)`。
4. 更新 `游戏状态管理.md`（权威游戏状态文档）。

## GameView 基类约定

所有游戏继承 `GameView`。关键方法：
- `update(dt: Float)` —— 每帧调用（dt 单位秒，已钳制到 0..0.05）
- `drawGame(canvas: Canvas)` —— 绘制游戏画面；基类自动处理暂停遮罩
- `reset()` —— 重置状态并自动开始
- `onTouch(event: MotionEvent)` —— 重写处理触摸输入
- `onLayout(w, h)` —— 尺寸变化时调用，用于初始化布局

游戏生命周期：`start()` → `pause()` / `resume()` → `gameOver(GameResult)`。`paused` 标志控制帧循环。`gameOver()` 冻结游戏并触发 Activity 的结算对话框。

**重要**：`pause()` 会调用 `invalidate()` 以立即显示暂停遮罩。不要删除这行 —— 这是 bug fix（issue #49）。

**帧循环去重**：`start()`/`resume()` 通过私有的 `scheduleFrame()` 挂起帧回调，并用 `frameScheduled`
标志保证幂等 —— 否则每按一次「↻」重开都会永久多出一份 Choreographer 回调（每帧执行 N 次 update）。
不要改回直接 `postFrameCallback`。

**`gameOver()` 有重入门闩**：`if (!running) return`。子类在递归（扫雷洪水填充）或多分支中重复调用
只会回调一次，不会叠出多个结算框、不会重复写历史。**子类仍然应该自己判 `over` 标志**，门闩只是兜底。

**方向锁定**：`GameActivity` 对非横屏游戏锁定竖屏。因为各游戏 `onLayout()` 末尾都会调用 `reset()`，
一旦旋转/尺寸变化就会静默清空对局，所以不要去掉这个锁定，也不要在 `onLayout()` 里无条件 `reset()`
（新增游戏请参考 `CurlingView` 的 `firstLayout` 写法：只在首次布局时开局）。

## 双人游戏约定

- 玩家 1 始终朝下（底部），玩家 2 朝上（顶部）。
- 玩家 2 的所有文字/UI 必须旋转 180°，使用 `drawPieceText(canvas, paint, text, cx, cy, facingUp=true)` 或 `canvas.rotate(180f)`。
- 所有双人游戏通过 `DualConsentView`（分屏同意页）进入 —— 详见 `游戏状态管理.md` §双人游戏视角约定。
- 带 AI 模式的游戏（五子棋、黑白棋）先进模式选择（双人 vs 电脑），再进入对应流程。
- **整屏盲拍的游戏**（反应力对决、四人反应、抢答算术、乒乓对决）在 `GameSpec` 里设 `sideControls = true`，
  悬浮控件会移到屏幕左右两侧的竖直居中位置。这些游戏的四个角正是玩家最常点击的区域，
  按钮留在角落会吃掉触摸，轻则漏判一次抢答，重则直接退出整局。
- **得分胶囊按需显示**：`GameActivity` 的右下角「得分 N」初始隐藏，只有游戏真正上报 `score > 0` 才出现。
  双人/多人棋类不计分，常驻的「得分 0」会压住游戏底部提示文字。
- 底部居中提示文字不要画在 `height - dp(55..12)` 区间内，那是得分胶囊的位置。

## 关键参考文档

- `游戏逻辑.md` —— AI Agent 专用游戏逻辑规格书（伪代码、边界条件、碰撞公式）。**实现或修改游戏逻辑前必须先读此文档。**
- `游戏状态管理.md` —— 权威游戏收录状态（哪些游戏已收录、暂缓、已删除）。必须与 `GameRegistry.games` 保持同步。
- `README.md` —— 面向用户的概览和构建说明。

## 测试

- **单元测试**（`app/src/test/`）：`CurlingPhysicsTest.kt`、`XiangqiRulesTest.kt` —— 纯逻辑测试，无 Android 依赖。
- **仪器化测试**（`app/src/androidTest/`，共 24 项）：
  - `AllGamesSmokeTest`：遍历注册表逐款启动 Activity
  - `GameRuntimeTest`：逐款构造 View + measure/layout + 多点触摸 + 180 帧步进；含 gameOver 幂等、帧回调不叠加
  - `GameLogicTest`：反射校验各棋类初始布局与规则
  - `MultiTouchTest`：双人/多人多点触控
  - `XiangqiCannonUiTest`：象棋「炮」吃子经 **UI 落子入口**（`onIntersectionTap`）的回归测试 ——
    锁定「隔炮架可吃士」与「无炮架不可吃」，避免把规则本身误当缺陷去改
  - `TestGames`：工具类，在主线程直接构造并布局游戏 View（**不要再用 ActivityScenario 后再 findView**——
    GameActivity 会先弹规则/同意页，拿不到游戏 View，本目录老测试就是这么全部 NPE 的）
- 需要模拟器（已在 Android 16 / API 36 验证）。运行 `gradlew.bat :app:test` 执行单元测试；
  `gradlew.bat :app:connectedAndroidTest` 执行仪器化测试。
  注意：debug APK 与 release APK 签名不同，跑仪器化测试前需先 `adb uninstall com.minigames.collection`。

## 音效与语音

- 音效为程序合成的 WAV 文件，位于 `res/raw/`（click、move、eat、win、lose）。
- 中文语音样本（象棋："将军"、"吃"、"将死" 等）位于 `res/raw/voice_xxx.wav`。添加新的 `voice_*.wav` 到 `res/raw/` 即可自动生效。
- 所有音频受 `SettingsStore` 开关控制（音效、震动、语音）。

## 开发约定

- 所有 UI 文字使用简体中文。
- 调色板定义在 `GameView.companion`（`C_BG`、`C_PANEL`、`C_TEXT` 等）—— 统一使用这些颜色。
- **桌游/棋类统一走 `BoardArt`**（`common/BoardArt.kt`）：木板底盘用 `drawWoodSlab`、圆棋子用
  `drawCreamPiece`、围棋/黑白棋棋子用 `drawGlassStone`、状态条用 `drawStatusBar`、按钮用
  `drawSoftButton`、文字用 `drawCenterText`。颜色一律从 `BoardArt.Palette` 取，**不要在游戏文件里
  再写字面色值** —— 否则又会退回到"9 款游戏 9 套配色"的老问题。木纹 Shader 在 `onLayout` 里用
  `buildWoodSlab` 预生成，并且只在棋盘尺寸真的变化时才重建（见 `GomokuView.lastBoardSide` 写法）。
- `dp()` 有 `Int` 与 `Float` 两个重载；需要小数尺寸时写 `dp(2.5f)` 即可（`GameView.dp(Float)`）。
- 游戏通过 `setScore(score)` 更新 Activity 的得分显示，通过 `gameOver(GameResult)` 结束游戏。
- `GameSpec` 中的 `landscape = true` 强制横屏（用于切水果、记忆对决）；其余游戏锁定竖屏。
- `GameSpec` 中的 `sideControls = true` 把悬浮控件从四角移到左右两侧居中（用于整屏盲拍的游戏）。

## 排版约定（棋盘类游戏）

- **顶部安全区**：`dp(SAFE_TOP_DP)`（=100dp）之内不要放任何游戏内容。悬浮控件（退出/设置/暂停/重开/帮助）
  就叠在四角，且比安全区最低沿还要低一点 —— 棋盘贴太紧时顶行会被「?」按钮压住（斗兽棋踩过这个坑，
  最终额外留 56dp）。
- **正方形棋盘不要按整屏高度居中**：竖屏手机上 `w * 0.94` 通常远小于屏高，居中会让棋盘"浮在屏幕中间"。
  正确做法是让棋盘紧贴头部信息之下开始、余量全部留在底部（五子棋/黑白棋/围棋/斗兽棋统一如此）。
- **给每段文字留出独立的 band**：多个元素叠在同一条基线上时（围棋的「提子」行 vs 棋盘顶边）会互相压住，
  布局时应显式分配纵向区间，而不是靠 `某元素.bottom + Ndp` 临时偏移。
- 底部居中提示文字不要画在 `height - dp(55..12)` 区间内，那是得分胶囊的位置。

## 输入与操作约定

- **按键动作不要用"数组下标"判定**。抢答算术的键盘是 `1..9 / ⌫ / 0 / ✓`，早期实现直接拿下标当动作码
  （`action <= 9` 视为数字、`action == 10` 视为删除），结果 ⌫（下标 9）被当数字追加进输入框、
  0（下标 10）反而退格。凡是有"按键数组"的地方，一律用具名动作码（见 `MathDuelView.ACT_DIGIT`）
  与键面数组一一对应，动作与下标彻底解耦。
- **方向类操作要区分"支点"与"落点"**。冰壶的瞄准是：按下点 = 固定支点 P，P→手指 的延长线与发射底线
  的**交点**才是抛出点（会沿底线滑动）；投出方向直接取解算结果，**不要**在投壶时再用「触点 − 抛出点」
  算一遍——抛出点本来就是从这条线解出来的，二次计算会把同一几何关系重复施加，球路偏离玩家看到的瞄准线。
- 无效输入要有明确判定并拒绝出手（冰壶：拖动 < 12dp、连线与底线平行、支点落在底线之下），
  不要用兜底方向"替玩家投出去"。


