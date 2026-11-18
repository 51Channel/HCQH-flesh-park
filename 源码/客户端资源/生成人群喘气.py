#!/usr/bin/env python3
"""
收尾录像里一屋子人一起喘：crowd_in.wav（吸，约 1.45 秒）、crowd_out.wav（呼，约 1.75 秒）。
原来用的是"它"（整口井）那两声低沉的五秒气流，降调一放就成了轰轰的噪音，不像人。这里按人嘴喘气合成：
- 吸：张嘴往里抽气的"嘶——"，气声集中在 1～3 kHz，开头慢慢起来，最后一下收住。
- 呼：一口"哈——"泄出去，气声更低（0.6～1.6 kHz），一部分人呼的时候喉咙里带一点"嗯"。
十八个人叠在一起，每人嗓子（共鸣高低）、大小、左右位置不一样，开始的时刻只差几十毫秒，听着是一屋子人同一口气。
最后加一点大屋子的回声。纯 Python，不用装别的。用法：python3 生成人群喘气.py <输出根目录>（同 生成声音.py，写到 assets/fleshpark/audio）。
"""
import math
import os
import random
import struct
import sys
import wave

RATE = 44100


class Band:
    """二阶带通（RBJ），共鸣峰可以慢慢挪。"""

    def __init__(self):
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0
        self.set(1000, 2)

    def set(self, f, q):
        w = 2 * math.pi * f / RATE
        alpha = math.sin(w) / (2 * q)
        a0 = 1 + alpha
        self.b0, self.b2 = alpha / a0, -alpha / a0
        self.a1, self.a2 = -2 * math.cos(w) / a0, (1 - alpha) / a0

    def run(self, x):
        y = self.b0 * x + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2, self.x1 = self.x1, x
        self.y2, self.y1 = self.y1, y
        return y


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def one(inhale, rnd):
    """一个人的一口气（单声道）。"""
    k = rnd.uniform(0.82, 1.22)  # 嗓子：共鸣整体高低
    if inhale:
        dur = rnd.uniform(1.25, 1.42)
        f1, f2, q1, q2 = 1250 * k, 2500 * k, 2.2, 3.2
    else:
        dur = rnd.uniform(1.45, 1.7)
        f1, f2, q1, q2 = 680 * k, 1450 * k, 1.8, 2.6
    n = int(dur * RATE)
    b1, b2, hiss = Band(), Band(), Band()
    hiss.set(4200 * k, 1.2)
    voiced = not inhale and rnd.random() < 0.45
    f0 = rnd.uniform(92, 135)
    phase = 0.0
    lp = 0.0
    wob_f, wob_p = rnd.uniform(3, 6), rnd.uniform(0, 6.28)
    out = []
    for i in range(n):
        t = i / n
        sec = i / RATE
        if i % 256 == 0:
            # 吸的时候嘴越张越开，共鸣往上走一点；呼的时候往下落
            g = 1 + (0.1 * t if inhale else -0.12 * t)
            b1.set(f1 * g, q1)
            b2.set(f2 * g, q2)
        if inhale:
            env = smooth(sec / 0.32) * (1 - smooth((sec - (dur - 0.16)) / 0.16)) * (0.75 + 0.25 * t)
        else:
            env = smooth(sec / 0.05) * (1 - t) ** 1.25
        env *= 1 + 0.12 * math.sin(2 * math.pi * wob_f * sec + wob_p)
        w = rnd.uniform(-1, 1)
        s = b1.run(w) * 1.0 + b2.run(w) * 0.55 + hiss.run(w) * (0.22 if inhale else 0.1)
        if voiced:
            # 喉咙里带出来的一点"嗯"：很轻，低通过的锯齿
            phase = (phase + f0 * (1 - 0.08 * t) / RATE) % 1
            lp += 0.06 * ((2 * phase - 1) - lp)
            s += lp * 0.35 * smooth(sec / 0.15) * (1 - t)
        out.append(s * env)
    return out


def crowd(inhale, seed):
    rnd = random.Random(seed)
    length = int((1.6 if inhale else 1.95) * RATE)
    left, right = [0.0] * length, [0.0] * length
    for p in range(18):
        # 前两个离镜头近，响一点；其余远近不一
        gain = rnd.uniform(0.75, 1.0) if p < 2 else rnd.uniform(0.25, 0.6)
        pan = rnd.uniform(-0.8, 0.8)
        lg, rg = gain * math.sqrt((1 - pan) / 2), gain * math.sqrt((1 + pan) / 2)
        start = int(rnd.uniform(0, 0.07) * RATE)
        for i, s in enumerate(one(inhale, rnd)):
            j = start + i
            if j >= length:
                break
            left[j] += s * lg
            right[j] += s * rg
    # 大屋子：几下稀疏的回声，越往后越闷
    for ch in (left, right):
        dry = ch[:]
        for delay, g in ((0.061, 0.22), (0.113, 0.17), (0.197, 0.12), (0.29, 0.07)):
            d = int(delay * RATE)
            y = 0.0
            for i in range(length - d):
                y += 0.35 * (dry[i] - y)
                ch[i + d] += y * g
    # 尾巴淡掉，不然下一口接上时咔一下
    fade = int(0.08 * RATE)
    for i in range(fade):
        k = 1 - i / fade
        left[length - fade + i] *= k
        right[length - fade + i] *= k
    return left, right


def write(path, left, right):
    peak = max(1e-6, max(max(abs(s) for s in left), max(abs(s) for s in right)))
    k = 0.9 / peak
    frames = bytearray()
    for a, b in zip(left, right):
        frames += struct.pack("<hh", int(a * k * 32767), int(b * k * 32767))
    with wave.open(path, "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(bytes(frames))


def main():
    target = os.path.join(sys.argv[1], "assets", "fleshpark", "audio")
    os.makedirs(target, exist_ok=True)
    write(os.path.join(target, "crowd_in.wav"), *crowd(True, 7071))
    print("crowd_in")
    write(os.path.join(target, "crowd_out.wav"), *crowd(False, 7072))
    print("crowd_out")


main()
