# 音效素材备份

v3.4.0已按用户要求接入26个短音效；本目录仍保留原始备份，APK使用app/src/main/res/raw内的运行副本。以下为最初备份记录。

从原仓库 tools/sfx 及 app/src/main/res/raw 复制，原文件保留。26 个新收集 WAV 与生成工具、来源说明及本地已有缓存一起备份。

- wav/：可直接转入后续引擎的音效。
- tools/sfx/README.md：原有来源与授权说明（老库记载为 Kenney CC0）。
- backup-manifest.json：本次备份的文件大小与 SHA256。
- 整个目录位于 godot/、app/src/main/ 之外，不打包进 APK，也不自动播放。

优先候选：board_place（落子）、board_slide（走子）、board_capture（吃子）、board_shuffle（骰子/洗牌）、ui_error（非法操作）、ui_confirm（开始）、win_jingle（结算）。其他界面、物理及街机音效均保留供后续游戏选择。旧有短音效与语音接口暂不改动。
本次已检查26个WAV头：均为PCM、单声道、22050Hz、16bit，时长约0.014—1.048秒。这里是格式及完整性检查，未对每个声音做主观试听评级。
