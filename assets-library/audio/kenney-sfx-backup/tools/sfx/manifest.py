"""音效素材清单：CC0（Kenney.nl）音效 → 项目 res/raw 目标文件。

每一条描述一个游戏音效事件。构建脚本（build_sfx.py）据此：
  1. 从 GitHub 镜像下载 OGG 源文件（CC0，可自由用于商业项目）
  2. 用 WASM 解码器解成 PCM
  3. 裁掉首尾静音、低通降采样、峰值归一化、加淡入淡出
  4. 输出成项目统一的 16bit / 22050Hz 单声道 WAV

字段说明：
  raw      —— 输出到 app/src/main/res/raw/<raw>.wav（同时是 R.raw.<raw>）
  pack     —— 素材包代号，见 PACKS
  src      —— 素材包内的文件名
  gain     —— 相对响度（1.0 = 最响档）。刻意做出层次：提示音轻、打击音重，
              避免"所有音效一样响"导致的听觉疲劳
  max_ms   —— 截断上限（毫秒）。SoundPool 只适合短音效，过长会拖慢解码
  desc     —— 用途说明（同步写进 SoundManager 的注释）
"""

BASE = "https://raw.githubusercontent.com/ETdoFresh/kenney.nl/master/"

# 素材包代号 → 仓库内相对路径（未做 URL 编码；下载时按需编码）。
# 全部为 CC0 1.0（Kenney.nl）。
PACKS = {
    "ui": "kenney_uiaudio/Audio/",
    "iface": "kenney_interfacesounds/Audio/",
    "impact": "kenney_impactsounds/Audio/",
    "digital": "kenney_digitalaudio/Audio/",
    "casino": "kenney_casinoaudio/Audio/",
    "rpg": "kenney_rpgaudio/Audio/",
    "jingle": "kenney_musicjingles/Audio/8-Bit jingles/",
}

# raw 名          包        源文件                         gain  max_ms  说明
SOUNDS = [
    # ---- 通用 UI / 菜单 ----
    ("ui_click",     "ui",      "click2.ogg",                 0.75,  180, "按钮/菜单点击"),
    ("ui_switch",    "ui",      "switch7.ogg",                0.75,  220, "开关切换（设置项、模式选择）"),
    ("ui_back",      "iface",   "back_002.ogg",               0.70,  260, "返回/取消"),
    ("ui_confirm",   "iface",   "confirmation_002.ogg",       0.80,  400, "确认/开始"),
    ("ui_error",     "iface",   "error_004.ogg",              0.85,  300, "无效操作/非法落子"),
    ("ui_tick",      "iface",   "tick_001.ogg",               0.50,  140, "倒计时滴答"),
    ("ui_rollover",  "ui",      "rollover3.ogg",              0.50,  140, "候选高亮/掠过"),

    # ---- 棋盘 / 桌游 ----
    ("board_place",  "casino",  "cardPlace1.ogg",             0.80,  300, "落子/放牌（木质质感）"),
    ("board_slide",  "casino",  "cardSlide2.ogg",             0.70,  320, "滑动棋子/推牌"),
    ("board_capture", "impact", "impactWood_medium_001.ogg",  0.95,  420, "吃子/提子（木质撞击）"),
    ("board_shuffle", "casino", "cardShuffle.ogg",            0.80,  700, "洗牌/发牌"),

    # ---- 街机 / 动作 ----
    ("hit_light",    "impact",  "impactGeneric_light_001.ogg", 0.70, 260, "轻碰撞（挡板反弹、撞墙）"),
    ("hit_heavy",    "impact",  "impactPunch_medium_001.ogg",  0.95, 360, "重击（打砖块碎裂、得分撞击）"),
    ("hit_metal",    "impact",  "impactMetal_light_002.ogg",   0.85, 320, "金属撞击（台球、冰壶）"),
    ("hit_glass",    "impact",  "impactGlass_light_001.ogg",   0.90, 340, "玻璃碎裂（消行、清屏）"),
    ("hit_soft",     "impact",  "impactSoft_medium_001.ogg",   0.60, 300, "柔软撞击（球类、软碰撞）"),

    # ---- 得分 / 连击 / 能力 ----
    ("coin",         "rpg",     "handleCoins.ogg",            0.85,  520, "吃金币/收集得分"),
    ("powerup",      "digital", "powerUp4.ogg",               0.85,  520, "加速/道具/升级"),
    ("combo",        "digital", "threeTone1.ogg",             0.90,  460, "连击/连续得分"),
    ("levelup",      "digital", "phaserUp4.ogg",              0.90,  560, "过关/升级"),
    ("countdown",    "digital", "lowThreeTone.ogg",           0.85,  520, "回合开始/倒计时结束"),
    ("laser",        "digital", "laser4.ogg",                 0.75,  300, "射击/发射"),
    ("zap",          "digital", "zap2.ogg",                   0.80,  320, "电击/命中特效"),
    ("explode",      "digital", "lowRandom.ogg",              1.00,  700, "爆炸/坠毁"),

    # ---- 结算（8-bit 短旋律，按音高走向挑选：上行=胜利，下行=失败）----
    # 这两条是"锦上添花"：既有 win/lose 保留给需要快速反馈的场合，
    # 结算弹窗弹出时用 jingle 把情绪收住。旋律比单音更有仪式感。
    ("win_jingle",   "jingle",  "jingles_NES12.ogg",          0.95,  900, "胜利结算（上行旋律）"),
    ("lose_jingle",  "jingle",  "jingles_NES13.ogg",          0.90, 1100, "失败结算（下行旋律）"),
]


def source_url(pack: str, src: str) -> str:
    """素材库中某个文件的下载地址（raw.githubusercontent，路径含空格需编码）。"""
    from urllib.parse import quote
    return BASE + quote(PACKS[pack] + src)


def source_repo_path(pack: str, src: str) -> str:
    """素材文件在 GitHub 仓库内的路径（供 API 回退下载用）。"""
    return PACKS[pack] + src
