#!/usr/bin/env python3
"""生成血肉公园的配音和音乐，全部是本机合成，没有外部素材。
用法：生成声音.py <输出目录>（写到 输出目录/assets/fleshpark/audio/*.wav）

- 广播：macOS 自带的 say 念中文，再做成喇叭的声音。之后要换真人配音，直接替换同名 wav。
- 电台：say 加环形调制和降采样，做成机械人声。
- 音乐和环境声：纯 Python 合成，循环的首尾接得上。
只有标准库，慢一点，但只在改台词或音乐时才要跑。
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
PA_VOICE = "Tingting"
RADIO_VOICE = "Rocko (Chinese (China mainland))"

DRIVER = {
    "drv_39": "你是第三十九个。前面那些，我都见过。",
    "drv_board": "体验官？上车吧。坐前面，后面的座位没擦。",
    "drv_wait": "外面冷。上来吧。",
    "drv_1": "坐稳了。还有十来分钟就到。",
    "drv_2": "任务书在你旁边座位上。公司让我确认你看过。",
    "drv_tv": "这片子是公园当年拍的。车上就剩这一盘带子。",
    "drv_off": "别听了。都是那一套。",
    "drv_3": "你是第十一个。前面那些也是我送的。",
    "drv_4": "一个月跑一趟。每回都是晚上，每回，只拉一个。",
    "drv_5": "回程的单子我这儿没有。到时候你问他们吧。",
    "drv_6": "到了。我只送到落客区，车不进站，公司规定。",
    "drv_9": "下车吧。门开着。",
    "drv_7": "下车吧。我还得赶回去。",
    "drv_8": "祝您体验愉快。公司让我这么说的。",
}

PA = {
    "pa_thanks": "感谢您的评价！安诺丹大家庭祝您下行愉快。",
    "pa_thanks2": "感谢您的评价。",
    "pa_improve": "我们会改进。",
    "pa_recorded": "已记录。",
    "pa_board": "请走进八一三号缆车。",
    "pa_open": "欢迎光临息壤国家公园。今日客流适中，预计排队时间，一百二十分钟。",
    "pa_count": "感谢光临。您是今日第一千二百四十二位游客。",
    "pa_depart": "缆车即将发车。请勿倚靠车门。",
    "pa_next_tram": "下一班缆车将于二十分钟后到达。",
    "pa_welcome": "欢迎来到下游客中心。",
    "pa_hold": "缆车临时停车。请勿离开车厢。",
    "pa_stopped": "缆车已停运。请前往休闲花园层等候。我们会改进。",
    "pa_locker": "请将个人物品存入柜内。",
    "pa_pool1": "请先咨询您的医生。",
    "pa_pool2": "请确认您的信仰能够承受。",
    "pa_pool3": "从此处起，园方不再负责。",
    "pa_special": "特别体验。未开发腺泡，请自行爬入。",
    "pa_coop": "感谢您的配合。",
    "pa_notover": "感谢您的配合。体验尚未结束。",
    "pa_rate": "请为本次体验评分。",
    "pa_again": "我们会改进。请再体验一次。",
    "pa_reopen": "欢迎光临。全园盛大重开。",
    "pa_end": "欢迎您，体验官。下一批游客将于二十分钟后到达。",
    "pa_settle": "体验官，请到结算窗口，交回您的评分表。",
    "pa_fixed": "您的评分已更正。感谢您的配合。",
    "pa_staff": "欢迎加入安诺丹大家庭。请走员工通道。",
    "pa_sure": "您确定吗？",
    "pa_reconsider": "请重新考虑。",
    "pa_close": "差一点。",
    "pa_window": "感谢您的评价。现在为您开放观景窗。",
    "pa_mirror": "请在镜前整理您的仪容。",
    "pa_sample": "样板区欢迎您。请为改进后的版本评分。",
    "pa_accept": "样板已验收。感谢您的配合。",
    "pa_self": "体验官样板，生长中。请您验收。",
    "pa_tomorrow": "感谢您的评价。息壤国家公园，明天上午九点，盛大重开。",
    "pa_full": "欢迎光临息壤国家公园。今日门票已售罄，请有序入园。",
}
# 接驳大巴电视里的宣传片：2007 年公园自己拍的，女声，欢快，磁带放旧了
PROMO_VOICE = "Flo (Chinese (China mainland))"
PROMO = {
    "promo_1": "欢迎来到息壤国家公园。",
    "promo_2": "在西得克萨斯的地底下，有一个活着的奇观。它比山更老，比城市更大。",
    "promo_3": "乘坐安诺丹缆车，一路下到它的身体里。",
    "promo_4": "下游客中心，羊膜温泉，支气管森林。每一处，都在呼吸。",
    "promo_5": "您的每一次评价，都会让它变得更好。",
    "promo_6": "七月四日，独立日烟花晚会。全家一起，不见不散。",
    # 结尾车上的新宣传片：画面是第十一个这一路上的实况。最后一句念到分数前带子花了
    "promo_r1": "息壤国家公园，即将重新开放。",
    "promo_r2": "第十一位体验官，用一个晚上，替您走完了全程。",
    "promo_r3": "游客中心，羊膜温泉，支气管森林。每一站，他都认真体验过。",
    "promo_r4": "每一处不满意的地方，我们都已经改进。",
    "promo_r5": "体验官给出的最终评分是",
    "promo_r6": "您对本视频满意吗？",
}
# 车上的收音机：本地新闻台
NEWS_VOICE = "Reed (Chinese (China mainland))"
NEWS = {
    "news_1": "本台消息。二叠纪公司今天表示，息壤国家公园将启动重新开放评估。",
    "news_2": "二零零七年七月四日晚，公园下游客中心垮塌，超过七百五十人遇难。公园关闭至今。",
    "news_3": "当年的事故报告称，下游客中心是整栋塌下去的。塌下去的地方，至今没有人下去过。",
    "news_4": "入口周边三公里仍为禁区。二叠纪公司称，评估由公司内部人员完成，目前评分良好。",
}
# 813 号缆车上的导览录音：2007 年的磁带，和接驳大巴上的宣传片是同一个女声
GUIDE = {
    "guide_1": "欢迎乘坐安诺丹缆车。本次缆车下行至下游客中心，全程约六分钟。请扶好扶手，请勿将手伸出窗外。",
    "guide_2": "一九七零年代，牧场工人在这片洼地里，发现了一个温热的洞口。一九七六年，安诺丹公司把它带给了全世界。",
    "guide_3": "前方就是息壤的入口。入口由扩张锚撑开，每天检修两次，请您放心。",
    "guide_4": "您现在来到的，是息壤的中央腔。下游客中心由六根支撑悬挂在腔顶，是世界上唯一一座建在有机体内部的游客中心。",
    "guide_5": "右手边是本线路的拍照点。请看向窗外，微笑。纪念照可以在三号码头领取。",
    "guide_6": "缆车即将到站。感谢您乘坐安诺丹缆车。安诺丹大家庭，祝您在息壤度过难忘的一天。",
}
# 813 号车行车录音带上的无线电：2007 年 7 月 4 日下午，调度老陈和司机周敏。开头谁都不当回事；后半截回话的不是周敏
CHEN_VOICE = "Grandpa (Chinese (China mainland))"
ZHOU_VOICE = "Sandy (Chinese (China mainland))"
DISPATCH = {
    "r1": (CHEN_VOICE, "八一三，门关不上就先走吧，老毛病了。上面还排着三百多号人呢。"),
    "r2": (ZHOU_VOICE, "行。门缝里夹着块肉，拔不下来，回来再说。"),
    "r3": (ZHOU_VOICE, "调度，管子今天收得有点紧啊，一直刮车皮。药是不是还没打？"),
    "r4": (CHEN_VOICE, "运药车让雨堵路上了。没事，开快点就过去了。"),
    "r5": (ZHOU_VOICE, "停了。……灯也灭了。车顶上有动静，咚，咚的。"),
    "r6": (CHEN_VOICE, "八一三？八一三回话。周敏？"),
    "r8": (CHEN_VOICE, "周敏？你车上现在几个人？"),
    "r10": (CHEN_VOICE, "八一三进站了。车门开着。……车上没人。人呢？"),
}
# 学周敏说话的那个：同一个声音，像机器在念
MIMIC = {
    "r7": "八一三。一切正常。我们会改进。",
    "r9": "三十二。",
}
# 旁白：开场的入场片和结尾的收尾，同一个男声，干净，不过喇叭
NARR_VOICE = "Eddy (Chinese (China mainland))"
NARR = {
    "n1": "西得克萨斯，二叠纪盆地。这条路的尽头，是息壤国家公园。",
    "n2": "地底下，埋着一个活着的巨大生物，人们管它叫，息壤。游客坐缆车往下，一路下到它的身体里面。",
    "n3": "它的身体里，有游客中心、温泉、餐厅，和纪念品店。最火的那几年，这里是全美国最热门的国家公园。",
    "n4": "二零零七年七月四日，下游客中心塌了，七百五十多人没上来。公园关了。后来接管这里的，是二叠纪公司。",
    "n5": "公司专门看着它，封锁、监测，可谁也不敢下去。我是公司最底层的研究员，每天在地面上，抄档案。",
    "n6": "去年，下面的评分终端，又开始往上传数据了。公司想知道下面现在什么样，要派人下去走一遍，每一站打个分。这个活儿叫体验官，一天八百美元。我报了名。",
    "e1": "池子里这一排，是二零零七年那晚没上岸的游客。准确地说，是它照着他们长出来的。",
    "e2": "你打的每一个分，它都收到了。你喜欢什么，它就长成什么。",
    "e3": "现在，它在照着你长。",
    "e4": "回收公司宣布，息壤国家公园明天重新开放。",
    "e5": "下一位体验官，二十分钟后到。",
}
STOP = {
    "stop_call": "已为您呼叫接驳车。请在黄线后等候。",
}
# 第十个体验官的手机录音：井底护林员那部电梯里。跟森林电台是同一个嗓子，没有电台那层处理
MEMO = {
    "m10_1": "……录上了吗。录上了。",
    "m10_2": "我是第十个。我掉下来了，坐电梯，掉下来的。",
    "m10_3": "上面那个楼……底下是肉。它是，长出来的。",
    "m10_4": "那边有个红灯。我过去看看。",
}

RADIO = {
    "radio_1": "有人吗？……这台对讲机上，写着十四号车。有人能听见吗？",
    "radio_2": "你是第几个了？",
    "radio_3": "它让你打分了，是吧。你打了几分？",
    "radio_4": "别打了。它不是在问你好不好玩。它在学。",
}


def write_wav(path, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    k = 0.92 / peak
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * k)) * 32767)) for s in samples))


def say(voice, text, rate):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "v.wav")
        subprocess.run(["say", "-v", voice, "-r", str(rate), "-o", path, "--file-format=WAVE", f"--data-format=LEI16@{RATE}", text], check=True)
        with wave.open(path, "rb") as wav:
            raw = wav.readframes(wav.getnframes())
    return [s / 32768 for s in struct.unpack(f"<{len(raw) // 2}h", raw)]


def lowpass(x, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    out, y = [], 0.0
    for s in x:
        y += a * (s - y)
        out.append(y)
    return out


def highpass(x, cutoff):
    low = lowpass(x, cutoff)
    return [a - b for a, b in zip(x, low)]


def echo(x, delay, gain, repeats=3):
    d = int(delay * RATE)
    out = x + [0.0] * (d * repeats)
    for r in range(1, repeats + 1):
        g = gain ** r
        for i, s in enumerate(x):
            out[i + d * r] += s * g
    return out


def tape(x, seed, wow=0.012, slow=0.95, gaps=0.03):
    """旧磁带：转速不稳，声音发沉，偶尔掉一下，底下一直有咝咝声和电流声。"""
    rnd = random.Random(seed)
    out = []
    pos = 0.0
    n = len(x)
    i = 0
    gap_left = 0
    while pos < n - 2:
        t = i / RATE
        pos += slow * (1 + wow * math.sin(2 * math.pi * 0.7 * t + seed) + 0.004 * math.sin(2 * math.pi * 9.3 * t))
        k = int(pos)
        f = pos - k
        s = x[k] + (x[min(k + 1, n - 1)] - x[k]) * f
        if gap_left > 0:
            gap_left -= 1
            s *= 0.08
        elif rnd.random() < gaps / RATE * 8:
            gap_left = int(rnd.uniform(0.03, 0.11) * RATE)
        s += rnd.uniform(-1, 1) * 0.012 + 0.012 * math.sin(2 * math.pi * 50 * t) + (rnd.uniform(-1, 1) * 0.5 if rnd.random() < 0.00035 else 0)
        out.append(s)
        i += 1
    return out


def pa_voice(text):
    """老商场的吊顶喇叭放旧磁带：很窄的频带，破音，大厅里的长回声。听得出在说什么，但不舒服。"""
    v = say(PA_VOICE, text, 165)
    v = tape(v, len(text), wow=0.013, slow=0.94, gaps=0.05)
    v = highpass(lowpass(v, 2500), 520)
    v = [round(math.tanh(s * 6.5) * 40) / 40 for s in v]
    v = highpass(lowpass(v, 3000), 380)
    # 开头结尾的电流声
    rnd = random.Random(len(text) + 7)
    head = [rnd.uniform(-1, 1) * 0.03 + 0.05 * math.sin(2 * math.pi * 100 * i / RATE) for i in range(3000)]
    return head + echo(v, 0.27, 0.42, 5) + head


def radio_voice(text):
    v = say(RADIO_VOICE, text, 150)
    v = tape(v, len(text) + 3, wow=0.02, slow=0.97, gaps=0.14)
    out = []
    held = 0.0
    rnd = random.Random(len(text))
    for i, s in enumerate(v):
        # 降采样、量化、环形调制：听着像一台机器在念
        if i % 3 == 0:
            held = round(s * 22) / 22
        ring = math.sin(2 * math.pi * 92 * i / RATE)
        out.append(held * (0.25 + 0.75 * ring) + held * 0.35 + rnd.uniform(-1, 1) * 0.02)
    out = highpass(lowpass(out, 2700), 420)
    return [0.0] * 3000 + [math.tanh(s * 4) for s in out] + [0.0] * 2000


def add_note(buf, start, table, gain):
    n = len(buf)
    for i, s in enumerate(table):
        buf[(start + i) % n] += s * gain


def ep_note(freq, seconds, bright=1.0):
    n = int(seconds * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-t * 2.6) * min(1, t * 200)
        out.append(env * (math.sin(2 * math.pi * freq * t) + 0.28 * bright * math.sin(4 * math.pi * freq * t) * math.exp(-t * 5) + 0.09 * bright * math.sin(14 * math.pi * freq * t) * math.exp(-t * 11)))
    return out


def midi(n):
    return 440 * 2 ** ((n - 69) / 12)


def mall_music():
    """商场里放的那种轻快爵士：八小节循环。"""
    bpm = 104
    beat = 60 / bpm
    bars = 8
    n = int(bars * 4 * beat * RATE)
    buf = [0.0] * n
    chords = [[60, 64, 67, 71], [57, 60, 64, 67], [62, 65, 69, 72], [55, 59, 62, 65], [60, 64, 67, 71], [65, 69, 72, 76], [62, 65, 69, 72], [55, 59, 62, 65]]
    bass = [36, 33, 38, 31, 36, 41, 38, 31]
    melody = [(0, 76), (1.5, 74), (2, 72), (3, 69), (4, 72), (5.5, 71), (6, 69), (7, 67), (8, 69), (9.5, 72), (10, 77), (11, 76), (12, 74), (14, 71), (16, 76), (17.5, 74), (18, 72), (19, 79), (20, 81), (21.5, 79), (22, 77), (23, 76), (24, 74), (25.5, 72), (26, 69), (27, 71), (28, 74), (30, 71)]
    cache = {}

    def table(key, f, sec, bright=1.0):
        if key not in cache:
            cache[key] = ep_note(f, sec, bright)
        return cache[key]

    for b, chord in enumerate(chords):
        at = b * 4 * beat
        for hit in (0, 1.5, 2.5, 3.5):
            for m in chord:
                add_note(buf, int((at + hit * beat) * RATE), table(("c", m), midi(m), 1.1), 0.075)
        add_note(buf, int(at * RATE), table(("b", bass[b]), midi(bass[b]), 1.4, 0.3), 0.24)
        add_note(buf, int((at + 2 * beat) * RATE), table(("b", bass[b] + 7), midi(bass[b] + 7), 1.2, 0.3), 0.2)
    for pos, m in melody:
        add_note(buf, int(pos * beat * RATE), table(("m", m), midi(m), 1.5, 0.6), 0.2)
    # 刷子鼓：一点点噪声
    rnd = random.Random(7)
    hat = [rnd.uniform(-1, 1) * math.exp(-i / 500) for i in range(2500)]
    for b in range(bars * 8):
        add_note(buf, int(b * beat / 2 * RATE), hat, 0.03 if b % 2 else 0.05)
    return lowpass(buf, 5200)


def spa_music():
    """温泉的音乐：很慢的长音，让人想睡。频率都是 1/16 赫兹的整数倍，首尾接得上。"""
    seconds = 16
    n = seconds * RATE
    base = [110, 165, 220, 277.25, 330, 415.25]
    freqs = [round(f * seconds) / seconds for f in base]
    out = []
    for i in range(n):
        t = i / RATE
        s = 0.0
        for k, f in enumerate(freqs):
            swell = 0.5 + 0.5 * math.sin(2 * math.pi * (k + 1) * t / seconds + k)
            s += math.sin(2 * math.pi * f * t) * swell / (1 + k * 0.4)
        out.append(s)
    return out


def noise_loop(seconds, shape, seed):
    """滤过的噪声，首尾交叉淡化成循环。shape(x, t) 决定音色。"""
    n = int(seconds * RATE)
    fade = int(1.5 * RATE)
    rnd = random.Random(seed)
    raw = shape([rnd.uniform(-1, 1) for _ in range(n + fade)])
    out = raw[:n]
    for i in range(fade):
        k = i / fade
        out[i] = out[i] * k + raw[n + i] * (1 - k)
    return out


def wind_cold(x):
    a, b = lowpass(x, 950), lowpass(x, 240)
    out = []
    for i in range(len(x)):
        t = i / RATE
        gust = 0.55 + 0.3 * math.sin(t * 0.7) + 0.15 * math.sin(t * 1.9 + 1)
        out.append((a[i] - b[i]) * gust)
    return out


def wind_hot(x):
    a = lowpass(x, 300)
    b = lowpass(x, 2200)
    return [a[i] * 2.6 + (b[i] - a[i]) * 0.3 * (0.6 + 0.4 * math.sin(i / RATE * 2.3)) for i in range(len(x))]


def rumble(x):
    a = lowpass(lowpass(x, 90), 90)
    return [s * 8 for s in a]


def drone():
    """空腔里的低鸣。"""
    seconds = 18
    n = seconds * RATE
    bed = noise_loop(seconds, rumble, 5)
    freqs = [round(f * seconds) / seconds for f in (41, 55, 61.7, 82.4)]
    out = []
    for i in range(n):
        t = i / RATE
        s = sum(math.sin(2 * math.pi * f * t + j) * (0.5 + 0.5 * math.sin(2 * math.pi * (j + 1) * t / seconds)) for j, f in enumerate(freqs))
        out.append(s * 0.3 + bed[i])
    return out


def moan():
    """支气管深处的呻吟：低沉的锯齿波穿过会动的共鸣峰。"""
    seconds = 8
    n = seconds * RATE
    y1 = y2 = 0.0
    phase = 0.0
    out = []
    for i in range(n):
        t = i / n
        env = math.sin(math.pi * t) ** 0.7
        f = 44 + 14 * math.sin(math.pi * t) + 2 * math.sin(i / RATE * 9)
        phase = (phase + f / RATE) % 1
        saw = 2 * phase - 1
        fc = 240 + 260 * math.sin(math.pi * t * 1.3) ** 2
        w = 2 * math.pi * fc / RATE
        q = 0.97
        y = 2 * q * math.cos(w) * y1 - q * q * y2 + saw * (1 - q)
        y2, y1 = y1, y
        out.append(env * (y * 3 + saw * 0.12))
    return echo(lowpass(out, 1400), 0.42, 0.5, 4)


def radio_static():
    rnd = random.Random(11)
    n = 5 * RATE
    x = highpass(lowpass([rnd.uniform(-1, 1) for _ in range(n)], 3000), 500)
    for _ in range(40):
        at = rnd.randrange(n - 200)
        for i in range(120):
            x[at + i] += rnd.uniform(-1, 1) * 1.5 * (1 - i / 120)
    return x


def wiper():
    """雨刮刮过脏玻璃：橡胶的吱吱声，来回一趟。"""
    seconds = 1.7
    n = int(seconds * RATE)
    rnd = random.Random(3)
    phase = 0.0
    out = []
    for i in range(n):
        t = i / n
        f = 1150 + 420 * math.sin(t * math.pi * 2) + rnd.uniform(-60, 60)
        phase += f / RATE
        rough = 0.5 + 0.5 * math.sin(i / RATE * 2 * math.pi * 37)
        out.append(math.sin(2 * math.pi * phase) * rough * math.sin(t * math.pi) ** 0.5 * 0.5 + rnd.uniform(-1, 1) * 0.06)
    return out


def drips():
    """空房间里的滴水和很远的闷响。"""
    seconds = 14
    n = seconds * RATE
    rnd = random.Random(21)
    buf = [0.0] * n
    for _ in range(11):
        at = rnd.randrange(n)
        f = rnd.uniform(620, 1250)
        for i in range(2600):
            t = i / RATE
            buf[(at + i) % n] += math.sin(2 * math.pi * (f + 500 * t) * t) * math.exp(-t * 24) * rnd.uniform(0.25, 0.5)
    bed = noise_loop(seconds, rumble, 9)
    return [a * 0.8 + b * 0.5 for a, b in zip(echo(buf, 0.23, 0.35, 2)[:n], bed)]


def buzz():
    """手机震动：两下，马达嗡嗡的，带一点桌面共振的咔咔声。"""
    n = int(1.05 * RATE)
    rnd = random.Random(7)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        on = (0.0 <= t < 0.36) or (0.55 <= t < 0.91)
        local = t if t < 0.5 else t - 0.55
        env = (min(1, local * 60) * min(1, (0.36 - local) * 60)) if on else 0.0
        f = 148 + 6 * math.sin(2 * math.pi * 9 * t)
        phase = (phase + f / RATE) % 1
        motor = math.sin(2 * math.pi * phase) + 0.45 * math.sin(4 * math.pi * phase) + 0.25 * (phase * 2 - 1)
        rattle = rnd.uniform(-1, 1) * 0.25 * (1 if phase < 0.08 else 0)
        out.append((motor * 0.6 + rattle) * env)
    out = highpass(out, 80)
    return [math.tanh(s * 1.6) * 0.8 for s in out]


def intercom_voice(text):
    """司机隔着隔板的对讲：窄频带，有点破，声音就在耳边，没有大厅的回声。"""
    v = say(RADIO_VOICE, text, 170)
    v = tape(v, len(text) + 11, wow=0.005, slow=0.99, gaps=0.0)
    v = highpass(lowpass(v, 3100), 420)
    v = [math.tanh(s * 3.2) * 0.7 for s in v]
    rnd = random.Random(len(text) + 31)
    click = [rnd.uniform(-1, 1) * 0.22 * math.exp(-i / RATE * 60) for i in range(int(0.08 * RATE))]
    hiss = [rnd.uniform(-1, 1) * 0.02 for _ in range(int(0.15 * RATE))]
    return click + hiss + v + hiss + click


def tv_voice(text):
    """车载电视的小喇叭放旧录像带：窄一点，有一点走带的抖，没有回声。"""
    v = say(PROMO_VOICE, text, 175)
    v = tape(v, len(text) + 5, wow=0.009, slow=0.98, gaps=0.01)
    v = highpass(lowpass(v, 4200), 330)
    v = [math.tanh(s * 2.2) * 0.8 for s in v]
    rnd = random.Random(len(text) + 13)
    return [s + rnd.uniform(-1, 1) * 0.015 for s in v]


def news_voice(text):
    """收音机里的新闻：中波的窄频带，底下一层沙沙声。"""
    v = say(NEWS_VOICE, text, 170)
    v = highpass(lowpass(v, 3000), 400)
    v = [math.tanh(s * 2.6) * 0.75 for s in v]
    rnd = random.Random(len(text) + 17)
    hiss = [rnd.uniform(-1, 1) * 0.03 for _ in range(int(0.25 * RATE))]
    return hiss + [s + rnd.uniform(-1, 1) * 0.03 for s in v] + hiss


def dispatch_voice(voice, text):
    """缆车上的对讲机：很窄，开口和收尾有咔哒一声，底下一层沙沙声，偶尔断一下。"""
    v = say(voice, text, 175)
    v = tape(v, len(text) + 23, wow=0.004, slow=0.99, gaps=0.04)
    v = highpass(lowpass(v, 2900), 450)
    v = [math.tanh(s * 3.5) * 0.7 for s in v]
    rnd = random.Random(len(text) + 41)
    click = [rnd.uniform(-1, 1) * 0.3 * math.exp(-i / RATE * 50) for i in range(int(0.1 * RATE))]
    hiss = [rnd.uniform(-1, 1) * 0.05 for _ in range(int(0.3 * RATE))]
    return click + hiss + [s + rnd.uniform(-1, 1) * 0.045 for s in v] + hiss + click


def mimic_voice(text):
    """学周敏说话的东西：周敏的声音，但是降采样、环形调制，一个字一个字地拼出来。"""
    v = say(ZHOU_VOICE, text, 150)
    v = tape(v, len(text) + 7, wow=0.02, slow=0.96, gaps=0.12)
    out = []
    held = 0.0
    for i, s in enumerate(v):
        if i % 3 == 0:
            held = round(s * 24) / 24
        t = i / RATE
        out.append(held * (0.55 + 0.45 * math.sin(2 * math.pi * 31 * t)))
    out = highpass(lowpass(out, 2600), 420)
    rnd = random.Random(len(text) + 43)
    hiss = [rnd.uniform(-1, 1) * 0.05 for _ in range(int(0.3 * RATE))]
    return hiss + [s + rnd.uniform(-1, 1) * 0.04 for s in out] + hiss


def memo_voice(text):
    """手机录音：电台那个嗓子，压低频、加一点底噪，近、喘。"""
    v = say(RADIO_VOICE, text, 140)
    v = highpass(lowpass(v, 3400), 300)
    rnd = random.Random(len(text))
    return [max(-1.0, min(1.0, x * 0.9 + (rnd.random() - 0.5) * 0.02)) for x in v]


def narr_voice(text):
    """旁白：像纪录片，离麦克风近，干净，一点点暖。"""
    v = say(NARR_VOICE, text, 150)
    v = lowpass(v, 7000)
    return [math.tanh(s * 1.4) * 0.8 for s in v]


def speaker_voice(text):
    """站牌上的小喇叭：很近，很窄，没有回声。"""
    v = say(PA_VOICE, text, 160)
    v = highpass(lowpass(v, 2800), 600)
    return [math.tanh(s * 4) * 0.7 for s in v]


def bus_loop():
    """坐在大巴前排：大柴油机更低更沉的脉冲、路面的轰轰声、车厢零件偶尔响一下。首尾交叉淡化成循环。"""
    seconds = 12
    n = int(seconds * RATE)
    fade = int(1.5 * RATE)
    rnd = random.Random(38)
    raw = [0.0] * (n + fade)
    phase = 0.0
    for i in range(n + fade):
        t = i / RATE
        f = 29 + 1.2 * math.sin(2 * math.pi * 0.11 * t)
        phase = (phase + f / RATE) % 1
        raw[i] = ((1 - phase) ** 5 * 2 - 0.3) * 0.55 + rnd.uniform(-1, 1) * 0.5
    raw = lowpass(raw, 240)
    road = highpass(lowpass([rnd.uniform(-1, 1) for _ in range(n + fade)], 700), 120)
    raw = [a + b * 0.12 for a, b in zip(raw, road)]
    at = 0.6
    while at < seconds:
        start = int(at * RATE)
        for i in range(int(0.04 * RATE)):
            if start + i < len(raw):
                raw[start + i] += rnd.uniform(-1, 1) * 0.1 * math.exp(-i / RATE * 80)
        at += rnd.uniform(0.5, 2.6)
    out = raw[:n]
    for i in range(fade):
        k = i / fade
        out[i] = out[i] * k + raw[n + i] * (1 - k)
    return out


def shutter():
    """卷帘门从门楣一路落下：每片帘板咔一下，越落越快，第 3 秒砸到地上，再有空屋子的回响。"""
    n = int(5.0 * RATE)
    rnd = random.Random(38)
    out = [0.0] * n

    def put(start, samples, gain=1.0):
        at = int(start * RATE)
        for i, s in enumerate(samples):
            if at + i < n:
                out[at + i] += s * gain

    def hit(seconds, cutoff, seed, decay):
        r = random.Random(seed)
        return lowpass([r.uniform(-1, 1) * math.exp(-i / RATE * decay) for i in range(int(seconds * RATE))], cutoff)

    at = 0.0
    k = 0
    while at < 2.95:
        put(at, hit(0.05, 3200, 40 + k, 70), 0.3 + 0.14 * at / 2.95)
        at += 0.085 - 0.04 * at / 2.95
        k += 1
    rattle = [rnd.uniform(-1, 1) * min(1.0, (i / RATE) / 0.4) * 0.5 for i in range(int(2.95 * RATE))]
    put(0, highpass(lowpass(rattle, 1500), 300), 0.28)
    put(3.0, hit(0.9, 150, 90, 6), 2.2)
    put(3.0, [math.sin(2 * math.pi * 212 * i / RATE) * math.exp(-i / RATE * 5) for i in range(int(1.2 * RATE))], 0.3)
    put(3.0, [math.sin(2 * math.pi * 523 * i / RATE) * math.exp(-i / RATE * 9) for i in range(int(0.7 * RATE))], 0.16)
    put(3.0, hit(0.12, 3000, 91, 30), 0.8)
    return [math.tanh(s * 1.1) * 0.9 for s in echo(out, 0.19, 0.3, 4)]


def scare():
    """惊吓：一下闷响，紧接着一声撕裂的尖叫，几个失谐的锯齿波往上滑，最后拖一条带磁带噪声的尾巴。"""
    n = int(1.8 * RATE)
    rnd = random.Random(66)
    out = []
    phases = [0.0] * 6
    for i in range(n):
        t = i / RATE
        hit = math.sin(2 * math.pi * (55 - 30 * t) * t) * math.exp(-t * 7) * 1.2
        env = min(1, t * 400) * math.exp(-t * 1.6)
        scream = 0.0
        for k in range(6):
            f = (720 + k * 37) * (1 + 0.5 * min(1, t * 1.5)) * (1 + 0.012 * math.sin(2 * math.pi * (6 + k) * t))
            phases[k] = (phases[k] + f / RATE) % 1
            scream += (phases[k] * 2 - 1) * (0.5 if k % 2 else 0.35)
        noise = rnd.uniform(-1, 1) * (0.9 * math.exp(-t * 9) + 0.15 * math.exp(-t * 1.2))
        out.append(hit + math.tanh(scream * env * 1.4) * 0.8 + noise)
    out = highpass(out, 60)
    return [math.tanh(s * 1.8) for s in out]


# 换班：面对面说话，不过喇叭。交班的司机是同一个声音（电台里的十四号车也是他）；第三十九个是个高高兴兴的年轻人
FACE_VOICE = "Rocko (Chinese (China mainland))"
FACE = {
    "hand_1": "我跟你说过。别打了。",
    "hand_2": "回程的单子，现在你拿到了。",
    "hand_3": "三十八个。我的班，交完了。",
    "hand_4": "二十分钟一趟。路，你认得。",
}
ROOKIE_VOICE = "Shelley (Chinese (China mainland))"
ROOKIE = {
    "t39_1": "师傅好！这是去息壤的车吧？",
    "t39_2": "网上都在排队呢，我运气好，抢到这个名额。",
    "t39_3": "师傅，您在这儿干多久了？",
    "t39_4": "哈哈，您真会开玩笑。",
    "t39_5": "谢谢师傅！回头给您打个五星！",
    "t12_1": "你好……我是新来的体验官。是在这儿报到吗？",
    "t12_2": "谢谢！回头给你们打个五星。",
}
NEWS_FINAL = {
    "nb_1": "本台消息。息壤国家公园重开评估未获通过，原定明天的开园推迟。",
    "nb_2": "据悉，第十一名体验官的最终评分，没有达到标准。",
    "nb_2s": "据悉，评估终端在第十一名体验官最后一次评分时损坏。",
    "nb_3": "已售出的一万两千张门票将全额退款。回收公司表示，将招募第十二名体验官，日薪上调至一千美元。",
    "nb_4": "问及前十一名体验官的下落，发言人只回答了一句：我们会改进。",
    "nf_1": "本台消息。息壤国家公园今天通过重开评估，明天上午九点，正式开放。",
    "nf_2": "评估依据是十一名志愿体验官交回的评分报告。平均得分，五点零。",
    "nf_3": "首日门票，四分钟售罄。通往园区的公路上，车队已经排出十二公里。",
    "nf_4": "记者追问体验官的下落。回收公司发言人称，全体体验官身体健康，已自愿留在园区工作。",
    "nf_6": "问及他们现在何处，发言人只回答了一句：我们会改进。",
}


def face_voice(voice, text):
    return say(voice, text, 165)


# 第四关：麻醉科。它半睡半醒地喘气、睡着以后的呼吸，和回收公司现场组的广播（第五关改进部里也是这个声音）
COMPANY_VOICE = "Reed (Chinese (China mainland))"
COMPANY = {
    "co_warn": "检测到镇静剂异常输注。体验官十一号，请立即回到规定路线。",
    "co_l5a": "体验官十一号，您已偏离规定路线。请在原地等候现场组。",
    "co_l5b": "……不用拦，让他看。体验官十一号，请前往样板区，继续您的评价。",
    "co_l6a": "体验官十一号，这里是地面指挥部。你现在在井下运营中心。下面，没有我们的人。",
    "co_l6b": "中控室的资料，你可以看。看完，请到接口平台，做最后一次评分。",
    "co_l6c": "接口平台已开放。开不开园，看你这一分。",
    "co_win": "……五分。通知售票处，明天九点开园。",
    "co_lose": "……没达标。开园推迟。通知人事，招第十二个。",
    "co_smash": "……终端坏了。开园推迟。通知人事，招第十二个。",
}


def company_voice(text):
    """回收公司现场组：平静的男声，从泵房的工业喇叭里出来。比园区那套旧喇叭新得多，没有磁带味，只有大空间的回声。"""
    v = say(COMPANY_VOICE, text, 172)
    v = highpass(lowpass(v, 4200), 260)
    v = [math.tanh(s * 2.2) * 0.8 for s in v]
    rnd = random.Random(len(text) + 51)
    click = [rnd.uniform(-1, 1) * 0.3 * math.exp(-i / RATE * 80) for i in range(int(0.06 * RATE))]
    return click + echo(v, 0.33, 0.35, 4)


def breath_in():
    """它吸气：低沉的气流越拉越紧，越吸越急，喉咙里带一点湿的响，最后一下憋住。约 5 秒，正好盖住黄灯和红灯。"""
    n = int(5.0 * RATE)
    rnd = random.Random(401)
    raw = [rnd.uniform(-1, 1) for _ in range(n)]
    low, mid = lowpass(raw, 380), lowpass(raw, 1600)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / n
        env = t ** 1.4 * (1 if t < 0.92 else max(0.0, (1 - t) / 0.08))
        f = 38 + 22 * t
        phase = (phase + f / RATE) % 1
        air = (mid[i] - low[i]) * (0.5 + 1.6 * t) + low[i] * 2.2
        wet = rnd.uniform(-1, 1) * 0.5 if rnd.random() < 0.0005 * (1 + 4 * t) else 0.0
        out.append((air + (2 * phase - 1) * 0.16 * t + wet) * env)
    return echo(lowpass(out, 2400), 0.31, 0.38, 4)


def breath_out():
    """它呼气：一大口温热的气流冲出来，慢慢泄掉，尾巴上一声低低的呻吟。约 5.5 秒。"""
    n = int(5.5 * RATE)
    rnd = random.Random(402)
    raw = [rnd.uniform(-1, 1) for _ in range(n)]
    low, mid = lowpass(raw, 260), lowpass(raw, 1100)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / n
        env = min(1.0, t * 14) * (1 - t) ** 1.6
        f = 52 - 18 * t
        phase = (phase + f / RATE) % 1
        moan = (2 * phase - 1) * 0.22 * math.sin(math.pi * min(1.0, t * 1.6))
        out.append((low[i] * 2.6 + (mid[i] - low[i]) * 0.7 + moan) * env)
    return echo(lowpass(out, 1800), 0.31, 0.38, 4)


def sleep_loop():
    """睡着以后：很慢很深的呼吸，吸六秒、呼十秒，几乎听不见。首尾都是零，直接循环。"""
    n = 16 * RATE
    rnd = random.Random(403)
    raw = [rnd.uniform(-1, 1) for _ in range(n)]
    low = lowpass(lowpass(raw, 200), 200)
    out = []
    for i in range(n):
        t = i / n
        env = 0.6 * math.sin(math.pi * t / 0.375) ** 2 if t < 0.375 else math.sin(math.pi * (t - 0.375) / 0.625) ** 1.5
        out.append(low[i] * env)
    return out


def main():
    target = os.path.join(sys.argv[1], "assets", "fleshpark", "audio")
    os.makedirs(target, exist_ok=True)
    only = set(sys.argv[2:])

    def emit(name, make):
        if only and name not in only:
            return
        write_wav(os.path.join(target, name + ".wav"), make())
        print(name)

    for name, text in PA.items():
        emit(name, lambda text=text: pa_voice(text))
    for name, text in RADIO.items():
        emit(name, lambda text=text: radio_voice(text))
    emit("mall", mall_music)
    emit("spa", spa_music)
    emit("drone", drone)
    emit("wind_cold", lambda: noise_loop(14, wind_cold, 1))
    emit("wind_hot", lambda: noise_loop(14, wind_hot, 2))
    emit("moan", moan)
    emit("radio_static", radio_static)
    emit("wiper", wiper)
    emit("drips", drips)
    emit("scare", scare)
    emit("buzz", buzz)
    emit("bus_loop", bus_loop)
    emit("shutter", shutter)
    for name, text in DRIVER.items():
        emit(name, lambda text=text: intercom_voice(text))
    for name, text in PROMO.items():
        emit(name, lambda text=text: tv_voice(text))
    for name, text in NEWS.items():
        emit(name, lambda text=text: news_voice(text))
    for name, text in STOP.items():
        emit(name, lambda text=text: speaker_voice(text))
    for name, text in NARR.items():
        emit(name, lambda text=text: narr_voice(text))
    for name, text in GUIDE.items():
        emit(name, lambda text=text: tv_voice(text))
    for name, (voice, text) in DISPATCH.items():
        emit(name, lambda voice=voice, text=text: dispatch_voice(voice, text))
    for name, text in MIMIC.items():
        emit(name, lambda text=text: mimic_voice(text))
    for name, text in FACE.items():
        emit(name, lambda text=text: face_voice(FACE_VOICE, text))
    for name, text in ROOKIE.items():
        emit(name, lambda text=text: face_voice(ROOKIE_VOICE, text))
    for name, text in NEWS_FINAL.items():
        emit(name, lambda text=text: news_voice(text))
    for name, text in COMPANY.items():
        emit(name, lambda text=text: company_voice(text))
    for name, text in MEMO.items():
        emit(name, lambda text=text: memo_voice(text))
    emit("breath_in", breath_in)
    emit("breath_out", breath_out)
    emit("sleep", sleep_loop)


main()
