# -*- coding: utf-8 -*-
"""
收尾：花园层水里漂着的那台搜救二队对讲机，最后录下的一段（2007 年 7 月 7 日凌晨，五个人接上绳下到了底）。
用法：生成对讲机录音.py <模组 generated_media 目录> <插件 resources 目录>
- 写 <generated_media>/assets/fleshpark/audio/walkie_rec.wav：整段一条（22050 Hz 单声道），对讲机的窄频带、开口收尾咔一声、底下一层沙沙声
- 写 <resources>/walkie.txt：每行 开始(tick)<TAB>长度(tick)<TAB>谁<TAB>字幕。插件照着出字幕。
配音用微软在线中文语音（edge-tts，要联网）：老周云健、小罗云希、大刘和三队云扬、吴医生晓晓。最后那句是它学老周说话，一个字一个字拼起来的，跟 813 磁带里学周敏那句一个做法。
这是老周头盔记录仪录的：现场的人直接录进去，井口上三队和吴医生的话从对讲机喇叭里出来。
"""
import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import time
import wave

RATE = 22050
ZHOU = ("老周", "zh-CN-YunjianNeural", "+0%")
LUO = ("小罗", "zh-CN-YunxiNeural", "+12%")
LIU = ("大刘", "zh-CN-YunyangNeural", "+8%")
WU = ("吴医生", "zh-CN-XiaoxiaoNeural", "+0%")
SAN = ("三队", "zh-CN-YunyangNeural", "+14%")
# 跟着它喘以后一起喊"这边"的几个：同一个词、同一个调
CHORUS = ["zh-CN-YunjianNeural", "zh-CN-YunxiNeural", "zh-CN-YunyangNeural", "zh-CN-YunxiaNeural"]

# 0.5.7：这是老周头盔记录仪录下的声音，现场的人说话直接录进去（near），井口上的三队和吴医生从对讲机喇叭里出来（radio）。
# 信息靠两件真人本来就会做的事说出来：队员看见什么喊什么；老周问上面该怎么办、再冲队员把命令吼一遍。
# 吴医生不讲来龙去脉，只说出了什么事、你们该干嘛。不打比方，一句只说一件事，每句都接上一句。

# (谁, 台词, 之后停多久秒, 前面垫几口喘气, 处理)
# 字幕一条最多 22 个字：插件先按句号拆，太长的再按逗号拆
# 台词里用 | 隔开的每一小段单独合成，接起来中间停一小下：一口气念完一整句就是念稿的味道。
# 段首记号：~ 慢一点、低一点（泄气、自言自语）；^ 往上挑（反问、没听清）；! 快、急；… 先停半秒再说（在想、在听）。
# 插件 Story.replay 按句子的序号排镜头：改了句子的条数或顺序，要一起改那边的 line(i)。
LINES = [
    (ZHOU, "三队，|二队到底了，|五个都在。", 0.6, 0, "near"),                  # 0 下楼梯
    (SAN, "收到。", 1.0, 0, "radio"),                                         # 1
    (LUO, "!队长！|!队长你来看", 0.4, 0, "nearcut"),                          # 2 一排排背影
    (ZHOU, "^咋了？", 0.6, 0, "near"),                                        # 3
    (LUO, "有人……|~好多人。", 1.4, 0, "near"),                                 # 4
    (ZHOU, "三队，|找着人了。|花园层，|坐了一屋子。", 0.6, 0, "near"),            # 5
    (SAN, "^活的吗？", 0.5, 0, "radio"),                                       # 6
    (ZHOU, "等会儿，|我看看。", 1.4, 0, "near"),                                # 7
    (LIU, "^大哥？|!大哥！|^听得见吗？|…队长，|拽不动，|~沉得要命。", 0.6, 0, "near"),  # 8 跳：人堆前头
    (LUO, "~脸怎么都是灰的……", 0.6, 0, "near"),                                # 9
    (ZHOU, "^有气没有？", 0.5, 0, "near"),                                     # 10
    (LIU, "有，|喘着呢。", 1.0, 0, "near"),                                    # 11
    (ZHOU, "都别动。|…你们听。", 5.0, 0, "near"),                               # 12 听：一屋子一起喘
    (LUO, "~怎么都一块儿喘啊。", 1.4, 0, "near"),                               # 13
    (ZHOU, "三队，|吴医生在不在？|让她听一下。", 1.4, 0, "near"),                  # 14
    (WU, "我是吴，|你说。", 0.5, 0, "radio"),                                  # 15
    (ZHOU, "人都有气，|就是叫不醒。|~脸是灰的。", 0.8, 0, "near"),                 # 16
    (WU, "…^喘气呢？", 0.6, 0, "radio"),                                       # 17
    (ZHOU, "喘气……|一块儿喘的。|~一屋子人一块儿。", 1.2, 0, "near"),               # 18
    (WU, "…~药车四号没到。", 0.5, 0, "radio"),                                 # 19
    (ZHOU, "^啥药车？", 0.6, 0, "near"),                                       # 20
    (WU, "给它打的药。|断三天了，|~它醒着呢。", 0.6, 0, "radio"),                  # 21
    (ZHOU, "^醒着会怎么样？", 0.8, 0, "near"),                                  # 22
    (WU, "它一喘……|人就跟着喘。|跟上了，|停不下来。|~人就那样了。", 0.8, 0, "radio"),  # 23
    (ZHOU, "那这些人", 0.1, 0, "nearcut"),                                     # 24
    (WU, "别管了。|你们别跟着喘，|!马上上来。", 0.6, 0, "radio"),                   # 25
    (ZHOU, "^救不了？", 1.0, 0, "near"),                                        # 26
    (WU, "~老周，|上来。", 0.8, 0, "radio"),                                    # 27
    (ZHOU, "!撤！|!都撤！|喘气自己数着，|!别跟他们！", 1.2, 0, "near"),              # 28
    (SAN, "老周……|你们先别急……|~井口这边……", 0.4, 0, "radio"),                    # 29 跳：楼梯口
    (ZHOU, "!说！", 0.6, 0, "near"),                                           # 30
    (SAN, "楼……|游客中心那个楼，|又长出来了。|~就在原来那位置。", 0.6, 0, "radio"),  # 31
    (ZHOU, "^塌下去那栋？", 0.6, 0, "near"),                                    # 32
    (SAN, "~一模一样……|窗户都是亮的。", 1.2, 0, "radio"),                         # 33
    (ZHOU, "~先上去再说。|小罗，|走了。", 1.0, 0, "near"),                         # 34
    (ZHOU, "^小罗？", 0.8, 0, "near"),                                         # 35 回头
    (LUO, "队长……|~这边。", 0.8, 2, "near"),                                    # 36
    (("几个人", None, None), "这边。　这边。", 1.0, 0, "chorus"),                  # 37 一起转过来
    (ZHOU, "小罗你起来……|~起", 1.4, 0, "nearcut"),                              # 38
    (ZHOU, "~别下来", 1.8, 2, "nearcut"),                                       # 39 掐断
    (ZHOU, "三队。二队一切正常。这边。", 1.6, 0, "mimic"),                         # 40 回到现在
]
# 字幕里显示的样子（合成的时候破折号会念怪，单独写）；其余的把记号去掉
SUBS = {
    2: "队长！队长你来看——",
    13: "……怎么都一块儿喘啊。",
    17: "……喘气呢？",
    19: "……药车四号没到。",
    24: "那这些人——",
    34: "……先上去再说。小罗，走了。",
    38: "小罗你起来……起……",
    39: "……别下来——",
}
# 这几句开口前先喘一口（自己的气，很轻）
GASP = {12, 28, 35}


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
        # 在线合成偶尔断一下：重试几次
        for attempt in range(5):
            done = subprocess.run([sys.executable, "-m", "edge_tts", "--voice", voice, f"--rate={rate}", f"--pitch={pitch}", "--text", text, "--write-media", mp3], capture_output=True)
            if done.returncode == 0:
                break
            if attempt == 4:
                raise RuntimeError(f"合成失败：{text} {done.stderr.decode(errors='replace')[-300:]}")
            time.sleep(2 + attempt * 2)
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


def near(x, seed):
    """头盔记录仪的麦克风录现场的人：比对讲机宽，小麦克风有点闷、有点破，带一点空屋子的回声。"""
    x = highpass(lowpass(x, 5200), 160)
    x = [math.tanh(v * 2.2) * 0.8 for v in norm(x)]
    d = int(0.09 * RATE)
    return [v + (x[i - d] * 0.18 if i >= d else 0) for i, v in enumerate(x)]


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
            if kind in ("cut", "nearcut"):
                # 话没说完，最后一个字咬掉一半（被打断、按键松开）
                v = v[:max(1, len(v) - int(0.16 * RATE))]
            v = near(v, k) if kind in ("near", "nearcut") else radio(v, k)
        if k in GASP:
            track += [v * 0.6 for v in breath_in] + [0.0] * int(0.12 * RATE)
        start = len(track)
        # 对讲机喇叭里出来的话前后咔一声（按键）；现场说话没有
        track += click(rnd) + v + click(rnd) if kind in ("radio", "cut", "chorus", "mimic") else v
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
