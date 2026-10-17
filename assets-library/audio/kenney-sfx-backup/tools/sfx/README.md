# 游戏音效素材（res/raw）

本目录存放音效素材的**来源说明与重新生成脚本**。音频文件本身在
`app/src/main/res/raw/`，由这里的脚本从 CC0 素材库生成。

## 素材来源与授权

全部素材来自 [Kenney.nl](https://kenney.nl) 的音效包，授权为 **CC0 1.0**
（<http://creativecommons.org/publicdomain/zero/1.0/>）：
可自由用于个人、教育和商业项目，**无需署名**。
本项目仍在此保留出处，以便追溯。

| 素材包 | 用途 | 来源 |
| --- | --- | --- |
| UI SFX | 界面点击、开关、掠过 | `kenney_uiaudio` |
| Interface Sounds | 返回、确认、错误、滴答 | `kenney_interfacesounds` |
| Impact Sounds | 碰撞（轻/重/金属/玻璃/柔软） | `kenney_impactsounds` |
| Digital Audio | 道具、连击、升级、激光、电击 | `kenney_digitalaudio` |
| Casino Audio | 落子、滑动、洗牌（桌游质感） | `kenney_casinoaudio` |
| RPG Audio | 金币/收集 | `kenney_rpgaudio` |
| Music Jingles (8-Bit) | 胜利/失败结算旋律 | `kenney_musicjingles` |

下载源为 GitHub 镜像仓库
[`ETdoFresh/kenney.nl`](https://github.com/ETdoFresh/kenney.nl)
（原始 OGG 文件，各包内附 `License.txt`）。

## 音效清单（26 个）

命名前缀即分组，便于按语义查找。`gain` 是生成时的相对响度档位——
刻意做出层次：轻提示音与重打击音不会"一样吵"。

### 界面反馈（ui_*）

| 文件 | 用途 | 来源 | gain | 时长 |
| --- | --- | --- | --- | --- |
| `ui_click.wav` | 按钮/菜单点击 | click2.ogg | 0.75 | 0.056s |
| `ui_switch.wav` | 开关切换（设置项、模式选择） | switch7.ogg | 0.75 | 0.167s |
| `ui_back.wav` | 返回/取消 | back_002.ogg | 0.70 | 0.070s |
| `ui_confirm.wav` | 确认/开始 | confirmation_002.ogg | 0.80 | 0.400s |
| `ui_error.wav` | 无效操作/非法落子 | error_004.ogg | 0.85 | 0.103s |
| `ui_tick.wav` | 倒计时滴答 | tick_001.ogg | 0.50 | 0.014s |
| `ui_rollover.wav` | 候选高亮/选中提示 | rollover3.ogg | 0.50 | 0.069s |

### 棋盘桌游（board_*）

| 文件 | 用途 | 来源 | gain | 时长 |
| --- | --- | --- | --- | --- |
| `board_place.wav` | 落子/放牌（木质质感） | cardPlace1.ogg | 0.80 | 0.300s |
| `board_slide.wav` | 滑动棋子/推牌/翻牌 | cardSlide2.ogg | 0.70 | 0.320s |
| `board_capture.wav` | 吃子/提子（木质撞击） | impactWood_medium_001.ogg | 0.95 | 0.260s |
| `board_shuffle.wav` | 洗牌/发牌/掷骰 | cardShuffle.ogg | 0.80 | 0.700s |

### 物理碰撞（hit_*）

| 文件 | 用途 | 来源 | gain | 时长 |
| --- | --- | --- | --- | --- |
| `hit_light.wav` | 轻碰撞（挡板反弹、撞墙） | impactGeneric_light_001.ogg | 0.70 | 0.118s |
| `hit_heavy.wav` | 重击（砖块碎裂、敲中目标） | impactPunch_medium_001.ogg | 0.95 | 0.270s |
| `hit_metal.wav` | 金属撞击（台球、冰壶） | impactMetal_light_002.ogg | 0.85 | 0.171s |
| `hit_glass.wav` | 玻璃碎裂（消行、清屏） | impactGlass_light_001.ogg | 0.90 | 0.145s |
| `hit_soft.wav` | 柔软撞击（球类、软碰撞） | impactSoft_medium_001.ogg | 0.60 | 0.183s |

### 得分与特效

| 文件 | 用途 | 来源 | gain | 时长 |
| --- | --- | --- | --- | --- |
| `coin.wav` | 吃金币/收集得分 | handleCoins.ogg | 0.85 | 0.520s |
| `powerup.wav` | 加速/道具 | powerUp4.ogg | 0.85 | 0.347s |
| `combo.wav` | 连击/连续得分 | threeTone1.ogg | 0.90 | 0.460s |
| `levelup.wav` | 过关/升级 | phaserUp4.ogg | 0.90 | 0.277s |
| `countdown.wav` | 回合开始/倒计时结束 | lowThreeTone.ogg | 0.85 | 0.520s |
| `laser.wav` | 射击/发射/出杆 | laser4.ogg | 0.75 | 0.300s |
| `zap.wav` | 电击/命中特效 | zap2.ogg | 0.80 | 0.320s |
| `explode.wav` | 爆炸/坠毁/踩雷 | lowRandom.ogg | 1.00 | 0.394s |

### 结算旋律

| 文件 | 用途 | 来源 | gain | 时长 |
| --- | --- | --- | --- | --- |
| `win_jingle.wav` | 胜利结算（**上行** 8-bit 旋律） | jingles_NES12.ogg | 0.95 | 0.852s |
| `lose_jingle.wav` | 失败结算（**下行** 8-bit 旋律） | jingles_NES13.ogg | 0.90 | 1.048s |

> 两条结算旋律是按**音高走向**从 17 条 8-bit jingle 里筛出来的：
> NES12 音高持续上行（胜利感），NES13 持续下行（失败感）。
> 与既有的 `win.wav` / `lose.wav`（短促单音，用于即时反馈）互补。

## 与既有音效的关系

`res/raw/` 下原有的 5 个合成音效（`click` / `move` / `eat` / `win` / `lose`）
**保持不变**，全项目 150+ 处调用不受影响。新音效是**补充**，不是替换：

- 原有音效：短促、单一，适合高频即时反馈（每次落子、每次移动）
- 新音效：有材质与层次（木质/金属/玻璃），适合有"分量"的事件
  （吃子、击碎、过关、结算）

## 重新生成

```bash
# 补齐缺失/过期的音效（源文件有缓存，不重复下载）
python tools/sfx/build_sfx.py

# 全部重新下载并生成
python tools/sfx/build_sfx.py --force

# 只打印清单
python tools/sfx/build_sfx.py --list
```

### 生成流程

1. **下载**：从 GitHub 镜像拉取 OGG 源文件（缓存在 `tools/sfx/cache/`）
2. **解码**：调用 Node + WASM 解码器（`decode_batch.mjs`）得到 float32 PCM
3. **后期**：混单声道 → 抗混叠降采样到 22050Hz → 去直流 → 裁静音
   → 峰值归一化（按 gain 分档）→ 淡入淡出
4. **写出**：16bit / 22050Hz / 单声道 WAV 到 `app/src/main/res/raw/`

输出格式与项目既有音效**完全一致**（22050Hz / 单声道 / 16bit），
这是 SoundPool 解码最快、也最省内存的格式。

### 依赖

- **Python 3**（仅标准库：`wave` / `struct` / `urllib`）
- **Node.js**（运行 WASM OGG 解码器；路径可用环境变量 `DSH_NODE` 覆盖）
- `tools/sfx/vendor/ogg-vorbis-decoder.min.mjs` —— 已随仓库提供，无需联网安装

### 新增音效

1. 在 `manifest.py` 的 `SOUNDS` 列表加一行
   （`raw 名, 包代号, 源文件名, gain, max_ms, 说明`）
2. 运行 `python tools/sfx/build_sfx.py`
3. 在 `SoundManager.kt` 加 `val` 引用，并加入 `ALL` 预加载列表

## 目录结构

```
tools/sfx/
├── README.md            本文件
├── manifest.py          音效清单（改这里来增删音效）
├── build_sfx.py         构建脚本（下载→解码→后期→写 WAV）
├── decode_batch.mjs     Node WASM 解码器调用入口
├── vendor/              随仓库提供的 OGG 解码器（@wasm-audio-decoders/ogg-vorbis，MIT）
└── cache/               源 OGG 与解码中间产物（可再生，可安全删除）
```
