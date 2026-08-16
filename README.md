# 🎮 迷你游戏合集 MiniGames v1.2.0

**纯离线**的安卓小游戏合集：单个 APK 安装后**无需联网**即可游玩，
内置 **53 款游戏**（30 单人 + 18 双人 + 5 多人），支持本地最高分、内置音效/震动/中文语音播报（可在设置中开关）、全屏沉浸式游玩、横竖屏自适应、多玩家**多点触控**同时操作。

## 📲 快速开始

| 版本 | 路径 | 说明 |
|---|---|---|
| **正式版** | 从 **GitHub Releases** 下载 `迷你游戏合集-v1.1.0.apk` | 已签名，可直接安装到 Android 13+ 手机 |
| 调试版 | `app/build/outputs/apk/debug/app-debug.apk` | 用于开发调试 |

安装后从桌面图标「迷你游戏合集」进入主菜单，按「单人 / 双人 / 多人」分类选择游戏。游戏内为**全屏沉浸式**，左上角退出/设置，右上角暂停/重开/帮助。

## 🎯 游戏列表（53 款）

### 👤 单人（32 款）
贪吃蛇、2048、俄罗斯方块、打地鼠、记忆翻牌、扫雷、**象棋（人机）**、跳一跳、别踩白块、数字华容道、见缝插针、切水果、小球躲避、飞镖、保龄球、合成大西瓜、弹一弹、打砖块、飞机大战、坦克大战、吃豆人、泡泡龙、羊了个羊、数独、三消、推箱子、三国华容道、迷宫、西蒙记忆、21 点、**五子棋（人机）**、**黑白棋（人机）**

### 👥 双人同屏（16 款）
五子棋、贪吃蛇对战、乒乓对决、井字棋、黑白棋、反应力对决、围棋、**象棋（双人）**、斗兽棋、暗棋、猜数字对决、记忆对决、方块对战、台球、冰壶对决、抢答算术

### 👨‍👩‍👧 多人同屏（5 款，3~4 人）
贪吃蛇四人战、反应力四人战、打地鼠四人战、飞行棋（2~4 人）、算术四人战

> 全部游戏支持横竖屏自适应；双人/多人游戏支持多点触控同时操作；
> 象棋/五子棋/黑白棋含 AI 对战版本；精品游戏带中文语音播报（将军/吃/将死等）。

## 🛠 技术栈

- 纯原生 **Kotlin + View/Canvas 自绘**，零第三方游戏引擎、零网络依赖
- **多点触控**：全部双人/多人游戏支持多名玩家同时操作各自分区（Instrumentation 测试验证）
- **横竖屏自适应**：竖屏手机、横屏平板均可游玩（平板体验更佳）
- **设置页**：音效/震动/语音播报开关（SharedPreferences 持久化）、清空最高分
- **中文语音播报**：内置"将军/吃/将死/悔棋/认输"等真人人声（素材索引：res/raw/voice_xxx.wav，自行添加即生效）
- `minSdk 33`（Android 13）~ `targetSdk 36`，单 APK 约 3MB
- 音效为程序合成的 WAV 全部内置（res/raw），象棋语音由中文 TTS 离线合成，均无外部素材
- 最高分存 SharedPreferences（`scores.xml`），仅 VIBRATE 普通权限，无需网络
- 构建：AGP 8.13.2 + Kotlin 2.1.21 + Gradle 8.14.3

## 🔧 构建方法

仓库根目录（Windows，注意路径含中文，`gradle.properties` 已配置
`android.overridePathCheck=true`）：

```
gradlew.bat :app:assembleRelease
```

或使用本机已下载的 Gradle：

```
.tools\gradle-8.14.3\bin\gradle.bat :app:assembleRelease
```

**签名信息**：`release.keystore`（alias: `minigames`，密码: `minigames123`）
——个人项目使用，如需上架请自行更换密钥。

## 📁 项目结构

```
app/src/main/java/com/minigames/collection/
├── MainActivity.kt          # 主菜单（分类筛选 + 游戏宫格）
├── GameActivity.kt          # 游戏统一容器（顶栏/暂停/重开/结算弹窗）
├── common/
│   ├── GameView.kt          # 游戏基类（帧循环/暂停/触摸/得分回调）
│   ├── GameSpec.kt          # 游戏注册表（新增游戏只需加一条）
│   ├── ScoreStore.kt        # 本地最高分
│   ├── SoundManager.kt      # 内置音效
│   └── SwipeHelper.kt       # 滑动识别
└── games/                   # 每款游戏一个包（全部继承 GameView）
    ├── snake/  game2048/  tetris/  whack/  memory/  mines/
    └── gomoku/ snakebattle/ pong/ tictactoe/ reversi/ reaction/
```

## ✅ 已验证

- 全部 12 款游戏在模拟器（Android 16 / API 36）启动、操作、结算无崩溃
- 分类筛选、游戏进入/返回、暂停/恢复、重开、最高分持久化均正常
- 截图见 `.tools/shots/`（菜单 + 12 款游戏）