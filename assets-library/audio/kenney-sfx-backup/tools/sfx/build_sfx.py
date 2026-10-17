"""构建游戏音效：CC0 素材 → app/src/main/res/raw/*.wav

流程：
  1. 下载（带缓存）：从 Kenney.nl 的 GitHub 镜像拉取 OGG 源文件
  2. 解码：调用 Node + WASM 解码器（decode_batch.mjs）得到 float32 PCM
  3. 后期：混单声道 → 抗混叠降采样到 22050Hz → 去直流 → 裁静音
          → 峰值归一化（按 manifest 的 gain 分档）→ 淡入淡出 → 写 16bit WAV

用法（在仓库根目录）：
    python tools/sfx/build_sfx.py            # 只补齐缺失/过期的音效
    python tools/sfx/build_sfx.py --force    # 全部重新生成
    python tools/sfx/build_sfx.py --list     # 只打印清单

设计说明：
  - 输出统一 16bit / 22050Hz / 单声道，与项目既有 click.wav 等保持一致；
    SoundPool 对短小的 16bit PCM 解码最快。
  - 归一化用「峰值 × gain」而不是统一拉满，这样轻提示音与重打击音有响度层次，
    不会出现"所有音效一样吵"的疲劳感。
  - 源文件缓存在 tools/sfx/cache/，重复构建不再走网络。
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import wave

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import SOUNDS, source_repo_path, source_url  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CACHE = os.path.join(HERE, "cache")
RAW_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")

TARGET_RATE = 22050
PEAK = 0.98          # 归一化后的峰值上限
FADE_MS = 2.0        # 首尾淡入淡出，消除爆音
SILENCE_FLOOR = 0.004  # 相对峰值的静音阈值
PAD_MS = 3.0         # 裁静音后保留的余量

REPO = "ETdoFresh/kenney.nl"
BRANCH = "master"
UA = {"User-Agent": "minigames-sfx-builder"}


# --------------------------------------------------------------------------- 下载


def _get(url: str, timeout: int = 120) -> bytes:
    req = urllib.request.Request(url, headers=UA)
    return urllib.request.urlopen(req, timeout=timeout).read()


def _download_via_api(repo_path: str) -> bytes:
    """回退通道：用 GitHub Contents API 取文件（raw.githubusercontent 在部分网络下被重置）。

    Contents API 对 >1MB 的文件返回 encoding=none 且不带内容，这里的音效都是几十 KB，
    不会触发该限制；仍保留断言以防将来换了更大的素材。
    """
    api = "https://api.github.com/repos/%s/contents/%s?ref=%s" % (
        REPO, urllib.parse.quote(repo_path), BRANCH)
    meta = json.loads(_get(api, timeout=60).decode("utf-8"))
    if meta.get("encoding") != "base64" or "content" not in meta:
        raise RuntimeError("API 未返回内联内容（文件过大？）：%s" % repo_path)
    return base64.b64decode(meta["content"])


def download(url: str, dest: str, repo_path: str, attempts: int = 3) -> str:
    """下载一个素材文件。先试 raw 直链，失败则回退到 GitHub API。

    返回实际使用的通道名（"raw" / "api"），便于在日志里看出走了哪条路。
    """
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    last_err: Exception | None = None

    for attempt in range(attempts):
        try:
            data = _get(url)
            if len(data) < 64:
                raise RuntimeError("下载内容过短")
            with open(dest, "wb") as f:
                f.write(data)
            return "raw"
        except Exception as exc:  # noqa: BLE001
            last_err = exc
            time.sleep(1.5 * (attempt + 1))

    # raw 通道不通：改用 API
    try:
        data = _download_via_api(repo_path)
        if len(data) < 64:
            raise RuntimeError("API 返回内容过短")
        with open(dest, "wb") as f:
            f.write(data)
        return "api"
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(
            "下载失败（raw 与 api 通道均不可用）\n  raw: %r\n  api: %r" % (last_err, exc))


def ensure_sources(force: bool) -> list[dict]:
    """下载所有源文件，返回解码任务列表。"""
    jobs = []
    for raw, pack, src, _gain, _max_ms, _desc in SOUNDS:
        ogg = os.path.join(CACHE, "%s__%s" % (pack, src))
        if force or not os.path.exists(ogg):
            url = source_url(pack, src)
            print("  下载 %s" % src, end=" ... ", flush=True)
            try:
                channel = download(url, ogg, source_repo_path(pack, src))
                print("OK (%d 字节, %s)" % (os.path.getsize(ogg), channel))
            except Exception as exc:  # noqa: BLE001
                print("失败")
                raise
        else:
            print("  缓存命中 %s" % os.path.basename(ogg))
        jobs.append({
            "in": ogg,
            "out": os.path.join(CACHE, raw + ".f32"),
            "meta": os.path.join(CACHE, raw + ".meta.json"),
            "raw": raw,
        })
    return jobs


# --------------------------------------------------------------------------- 解码


def decode_all(jobs: list[dict], force: bool) -> dict:
    """调用 Node WASM 解码器。返回 raw -> (f32 路径, meta)。"""
    todo = []
    metas = {}
    for job in jobs:
        meta_path = job["meta"]
        if not force and os.path.exists(meta_path) and os.path.exists(job["out"]):
            metas[job["raw"]] = (job["out"], json.load(open(meta_path)))
            continue
        todo.append(job)

    if todo:
        node = os.environ.get("DSH_NODE") or "node"
        jobs_file = os.path.join(CACHE, "_jobs.json")
        with open(jobs_file, "w", encoding="utf-8") as f:
            json.dump([{k: v for k, v in j.items() if k != "raw"} for j in todo], f)
        print("  解码 %d 个文件 ..." % len(todo))
        out = subprocess.run(
            [node, os.path.join(HERE, "decode_batch.mjs"), jobs_file],
            capture_output=True, text=True, cwd=HERE,
        )
        if out.returncode != 0:
            raise RuntimeError("解码失败：\n%s\n%s" % (out.stdout, out.stderr))
        for job in todo:
            metas[job["raw"]] = (job["out"], json.load(open(job["meta"])))
    return metas


# --------------------------------------------------------------------------- DSP


def read_f32(path: str, channels: int) -> list[float]:
    with open(path, "rb") as f:
        blob = f.read()
    vals = struct.unpack("<%df" % (len(blob) // 4), blob)
    if channels == 1:
        return list(vals)
    # 多声道混单声道
    mono = []
    for i in range(0, len(vals) - channels + 1, channels):
        mono.append(sum(vals[i:i + channels]) / channels)
    return mono


def moving_average(x: list[float], window: int) -> list[float]:
    """滑动平均低通，抑制降采样时的混叠。"""
    if window <= 1:
        return x
    out = [0.0] * len(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += v
        if i >= window:
            acc -= x[i - window]
        out[i] = acc / min(i + 1, window)
    return out


def resample(x: list[float], src_rate: int, dst_rate: int) -> list[float]:
    if src_rate == dst_rate:
        return x
    ratio = src_rate / dst_rate
    # 先低通再线性插值：廉价但对短音效足够，能避免高频"沙沙"混叠
    x = moving_average(x, max(1, int(round(ratio))))
    n_out = int(len(x) / ratio)
    out = [0.0] * n_out
    for i in range(n_out):
        pos = i * ratio
        i0 = int(pos)
        frac = pos - i0
        a = x[i0] if i0 < len(x) else 0.0
        b = x[i0 + 1] if i0 + 1 < len(x) else a
        out[i] = a + (b - a) * frac
    return out


def trim_silence(x: list[float], rate: int) -> list[float]:
    if not x:
        return x
    peak = max(abs(v) for v in x)
    if peak <= 0:
        return x
    floor = peak * SILENCE_FLOOR
    start, end = 0, len(x) - 1
    while start < end and abs(x[start]) < floor:
        start += 1
    while end > start and abs(x[end]) < floor:
        end -= 1
    pad = int(rate * PAD_MS / 1000.0)
    start = max(0, start - pad)
    end = min(len(x) - 1, end + pad)
    return x[start:end + 1]


def remove_dc(x: list[float]) -> list[float]:
    if not x:
        return x
    mean = sum(x) / len(x)
    return [v - mean for v in x]


def apply_fades(x: list[float], rate: int) -> list[float]:
    n = int(rate * FADE_MS / 1000.0)
    if n <= 0 or len(x) < 2 * n:
        return x
    out = list(x)
    for i in range(n):
        g = i / n
        out[i] *= g
        out[-1 - i] *= g
    return out


def normalize(x: list[float], gain: float) -> list[float]:
    peak = max((abs(v) for v in x), default=0.0)
    if peak <= 0:
        return x
    scale = (PEAK * gain) / peak
    return [max(-1.0, min(1.0, v * scale)) for v in x]


def write_wav(path: str, x: list[float], rate: int) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    frames = b"".join(struct.pack("<h", int(round(v * 32767))) for v in x)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(frames)


# --------------------------------------------------------------------------- 主流程


def build(force: bool, only_list: bool) -> int:
    if only_list:
        print("%-16s %-8s %-34s %5s %7s  %s" % ("raw", "包", "源文件", "gain", "max_ms", "说明"))
        for raw, pack, src, gain, max_ms, desc in SOUNDS:
            print("%-16s %-8s %-34s %5.2f %7d  %s" % (raw, pack, src, gain, max_ms, desc))
        return 0

    print("[1/4] 准备素材（CC0 / Kenney.nl）")
    jobs = ensure_sources(force)

    print("[2/4] 解码 OGG → PCM")
    metas = decode_all(jobs, force)

    print("[3/4] 后期处理并写出 WAV → %s" % os.path.relpath(RAW_DIR, ROOT))
    total_bytes = 0
    for raw, _pack, src, gain, max_ms, _desc in SOUNDS:
        f32_path, meta = metas[raw]
        samples = read_f32(f32_path, meta["channels"])
        samples = remove_dc(samples)
        samples = resample(samples, meta["sampleRate"], TARGET_RATE)
        samples = trim_silence(samples, TARGET_RATE)

        cap = int(TARGET_RATE * max_ms / 1000.0)
        if len(samples) > cap:
            samples = samples[:cap]

        samples = normalize(samples, gain)
        samples = apply_fades(samples, TARGET_RATE)

        out = os.path.join(RAW_DIR, raw + ".wav")
        write_wav(out, samples, TARGET_RATE)
        size = os.path.getsize(out)
        total_bytes += size
        print("  %-16s %5.3fs  %6d 字节   ← %s" % (raw, len(samples) / TARGET_RATE, size, src))

    print("[4/4] 完成：%d 个音效，合计 %.1f KB" % (len(SOUNDS), total_bytes / 1024.0))
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="构建 CC0 游戏音效到 res/raw")
    ap.add_argument("--force", action="store_true", help="忽略缓存，全部重新下载并生成")
    ap.add_argument("--list", action="store_true", help="只打印音效清单")
    args = ap.parse_args()
    return build(args.force, args.list)


if __name__ == "__main__":
    raise SystemExit(main())
