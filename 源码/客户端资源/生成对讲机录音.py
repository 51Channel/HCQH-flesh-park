# -*- coding: utf-8 -*-
"""
收尾：花园层水里漂着的那台搜救二队对讲机，最后录下的一段（2007 年 7 月 7 日凌晨，五个人接上绳下到了底）。
用法：生成对讲机录音.py <模组 generated_media 目录> <插件 resources 目录>
- 写 <generated_media>/assets/fleshpark/audio/walkie_rec.wav：整段一条（22050 Hz 单声道），对讲机的窄频带、开口收尾咔一声、底下一层沙沙声
- 写 <resources>/walkie.txt：每行 开始(tick)<TAB>长度(tick)<TAB>谁<TAB>字幕。插件照着出字幕。
配音用微软在线中文语音（edge-tts，要联网）：老周云健、小罗云希。最后那句是它学老周说话，一个字一个字拼起来的，跟 813 磁带里学周敏那句一个做法。
只听得见老周这一头，他在跟井口上的三队和吴医生说话，不对着观众讲。
"""
import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

RATE = 22050
ZHOU = ("老周", "zh-CN-YunjianNeural", "+0%")
LUO = ("小罗", "zh-CN-YunxiNeural", "+12%")
# 跟着它喘以后一起喊"这边"的几个：同一个词、同一个调
CHORUS = ["zh-CN-YunjianNeural", "zh-CN-YunxiNeural", "zh-CN-YunyangNeural", "zh-CN-YunxiaNeural"]

# 只听得见老周这一头：三队和吴医生在上面说什么听不见，从老周的回话和重复里听出来（像在旁边听别人打电话）。
# 停顿就是对面在说话。药断了、它醒着、跟着喘会停不下来、救不了、井口长出一栋楼，都靠老周重复对面的话漏出来。

# (谁, 台词, 之后停多久秒, 前面垫几口喘气, 处理)
# 字幕一条最多 22 个字：插件先按句号拆，太长的再按逗号拆
# 台词里用 | 隔开的每一小段单独合成，接起来中间停一小下：一口气念完一整句就是念稿的味道。
# 段首记号：~ 慢一点、低一点（泄气、自言自语）；^ 往上挑（反问、没听清）；! 快、急；… 先停半秒再说（在想、在听）。
LINES = [
    (ZHOU, "三队，|我们下来了。", 1.4, 0, "radio"),
    (ZHOU, "嗯，|人找着了，|~好多，|都坐着呢。", 1.6, 0, "radio"),
    (ZHOU, "不是，|叫不醒。|…你让吴医生听一下。", 1.8, 0, "radio"),
    (ZHOU, "对，|他们喘气是同步的。|…这么多人，|一起吸气，|一起呼气，|~跟一个人似的。", 2.2, 0, "radio"),
    (ZHOU, "^药？", 1.2, 0, "radio"),
    (ZHOU, "^断了几天了？", 1.4, 0, "radio"),
    (ZHOU, "~那它是|^醒着的？", 2.0, 0, "radio"),
    (ZHOU, "行，|~知道了，|不跟着喘。|…那这些人呢？", 1.8, 0, "radio"),
    (ZHOU, "~救不了是吧。", 1.8, 0, "radio"),
    (ZHOU, "^啥楼？|^井口？", 1.2, 0, "radio"),
    (ZHOU, "你说清楚点，|什么叫长出来一栋", 1.2, 0, "cut"),
    (ZHOU, "!行行行，|!我们上去。|!小罗！|!走了！", 1.0, 0, "radio"),
    (ZHOU, "^小罗？", 0.8, 0, "radio"),
    (LUO, "队长，|~这边。", 0.8, 3, "radio"),
    (("几个人", None, None), "这边。　这边。", 1.0, 0, "chorus"),
    (ZHOU, "别下来", 1.8, 2, "cut"),
    (ZHOU, "三队。二队一切正常。这边。", 1.6, 0, "mimic"),
]
# 字幕里显示的样子（合成的时候破折号会念怪，单独写）；其余的把记号去掉
SUBS = {
    6: "那它是醒着的？",
    10: "你说清楚点，什么叫长出来一栋——",
    15: "……别下来——",
}
# 这几句开口前先喘一口（自己的气，很轻）
GASP = {3, 7, 11}


def plain(text):
    return "".join(seg.lstrip("~^!…") for seg in text.split("|"))


def speak(who, text, seed):
    """一小段一小段合成再接起来：段和段之间停 0.1～0.3 秒，按记号改快慢、高低。"""
    rnd = random.Random(seed)
    out = []
    base = int(who[2].rstrip("%"))
    for i, seg in enumerate(text.split("|")):
        rate, pitch = base, 0
        if seg.startswith("…"):
            out += [0.0] * int(0.45 * RATE)
            seg = seg[1:]
        if seg.startswith("~"):
            rate, pitch, seg = base - 12, -4, seg[1:]
        elif seg.startswith("^"):
            rate, pitch, seg = base - 2, 7, seg[1:]
        elif seg.startswith("!"):
            rate, pitch, seg = base + 14, 3, seg[1:]
        if i > 0:
            out += [0.0] * int(rnd.uniform(0.08, 0.26) * RATE)
        out += tts(who[1], f"{rate:+d}%", seg, f"{pitch:+d}Hz")
    return out


def tts(voice, rate, text, pitch="+0Hz"):
    with tempfile.TemporaryDirectory() as tmp:
        mp3 = os.path.join(tmp, "v.mp3")
        wav = os.path.join(tmp, "v.wav")
        subprocess.run([sys.executable, "-m", "edge_tts", "--voice", voice, f"--rate={rate}", f"--pitch={pitch}", "--text", text, "--write-media", mp3], check=True, capture_output=True)
        subprocess.run(["afconvert", "-f", "WAVE", "-d", f"LEI16@{RATE}", "-c", "1", mp3, wav], check=True)
        with wave.open(wav, "rb") as w:
            raw = w.readframes(w.getnframes())
    s = [v / 32768 for v in struct.unpack(f"<{len(raw) // 2}h", raw)]
    lo, hi = 0, len(s)
    while lo < hi and abs(s[lo]) < 0.008:
        lo += 1
    while hi > lo and abs(s[hi - 1]) < 0.008:
        hi -= 1
    return s[lo:hi]


def read_wav(path):
    with wave.open(path, "rb") as w:
        raw = w.readframes(w.getnframes())
        rate = w.getframerate()
    s = [v / 32768 for v in struct.unpack(f"<{len(raw) // 2}h", raw)]
    if rate != RATE:
        k = rate / RATE
        s = [s[min(len(s) - 1, int(i * k))] for i in range(int(len(s) / k))]
    return s


def lowpass(x, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    out, y = [], 0.0
    for v in x:
        y += a * (v - y)
        out.append(y)
    return out


def highpass(x, cutoff):
    low = lowpass(x, cutoff)
    return [a - b for a, b in zip(x, low)]


def norm(x, peak=0.8):
    m = max(1e-6, max(abs(v) for v in x))
    return [v * peak / m for v in x]


def radio(x, seed):
    """对讲机：很窄，有点破，偶尔断一下。"""
    rnd = random.Random(seed)
    x = highpass(lowpass(highpass(lowpass(x, 3000), 380), 2900), 420)
    x = [math.tanh(v * 5) * 0.75 for v in norm(x)]
    out, gap = [], 0
    for v in x:
        if gap > 0:
            gap -= 1
            v *= 0.15
        elif rnd.random() < 0.6 / RATE:
            gap = int(rnd.uniform(0.03, 0.08) * RATE)
        out.append(v)
    return out


def mimic(chars, seed):
    """它学老周说话：老周的声音，一个字一个字拼起来，降采样、环形调制，像机器在念。"""
    rnd = random.Random(seed)
    v = []
    for c in chars:
        if c in "。，":
            v += [0.0] * int(rnd.uniform(0.28, 0.42) * RATE)
            continue
        piece = tts(ZHOU[1], "+0%", c)
        v += piece + [0.0] * int(rnd.uniform(0.04, 0.12) * RATE)
    out, held = [], 0.0
    for i, s in enumerate(norm(v)):
        if i % 3 == 0:
            held = round(s * 24) / 24
        out.append(held * (0.55 + 0.45 * math.sin(2 * math.pi * 31 * i / RATE)))
    out = highpass(lowpass(out, 2600), 420)
    return [math.tanh(s * 4) * 0.8 for s in out]


def breath(x, seconds):
    """一口喘气：从有声音的地方截一秒左右，头尾淡一下，过对讲机。"""
    lo = 0
    while lo < len(x) and abs(x[lo]) < 0.02:
        lo += 1
    n = int(seconds * RATE)
    x = x[lo:lo + n]
    fade = int(0.12 * RATE)
    x = [v * min(1, i / fade, (len(x) - i) / fade) for i, v in enumerate(x)]
    return norm(highpass(lowpass(x, 2800), 400), 0.35)


def click(rnd):
    return [rnd.uniform(-1, 1) * 0.6 * math.exp(-i / RATE * 45) for i in range(int(0.09 * RATE))]


def main():
    audio = os.path.join(sys.argv[1], "assets", "fleshpark", "audio")
    rnd = random.Random(7)
    breath_in = breath(read_wav(os.path.join(audio, "breath_in.wav")), 0.9)
    breath_out = breath(read_wav(os.path.join(audio, "breath_out.wav")), 1.0)
    track = [0.0] * int(1.0 * RATE)
    rows = []
    for k, (who, text, after, breaths, kind) in enumerate(LINES):
        # 前面垫几口喘气：一口比一口慢，跟着那个节奏
        for b in range(breaths):
            track += breath_in + [0.0] * int((0.15 + b * 0.12) * RATE) + breath_out + [0.0] * int((0.2 + b * 0.15) * RATE)
        if kind == "chorus":
            parts = [tts(v, "+0%", "这边。") for v in CHORUS]
            n = max(len(p) for p in parts)
            one = [sum(p[i] if i < len(p) else 0 for p in parts) for i in range(n)]
            v = radio(one + [0.0] * int(0.7 * RATE) + one, k)
        elif kind == "mimic":
            v = [0.0] * int(0.4 * RATE) + mimic(text, k)
        else:
            v = speak(who, text, k)
            if kind == "cut":
                # 话没说完，最后一个字咬掉一半，按键松开
                v = v[:max(1, len(v) - int(0.16 * RATE))]
            v = radio(v, k)
        if k in GASP:
            track += [v * 0.6 for v in breath_in] + [0.0] * int(0.12 * RATE)
        start = len(track)
        track += click(rnd) + v + click(rnd)
        rows.append(f"{round(start / RATE * 20)}\t{max(1, round((len(track) - start) / RATE * 20))}\t{who[0]}\t{SUBS.get(k, plain(text))}")
        track += [0.0] * int(after * RATE)
        print(k, who[0], round((len(track) - start) / RATE, 2))
    track += [0.0] * int(0.6 * RATE)
    # 底下一层沙沙声，一直有
    hiss = highpass(lowpass([rnd.uniform(-1, 1) for _ in range(len(track))], 3200), 300)
    track = [t + h * 0.05 for t, h in zip(track, hiss)]
    track = norm(track, 0.92)
    with wave.open(os.path.join(audio, "walkie_rec.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in track))
    with open(os.path.join(sys.argv[2], "walkie.txt"), "w", encoding="utf-8") as f:
        f.write(f"total\t{round(len(track) / RATE * 20)}\t\t\n")
        f.write("\n".join(rows) + "\n")
    print("总长", round(len(track) / RATE, 1), "秒")


main()
