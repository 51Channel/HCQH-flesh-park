#!/usr/bin/env python3
"""给 --client-session 发一串步骤。用法：驱动.py <会话目录> <步骤文件>
步骤每行一条：
  s <命令>         发给服务端控制台
  c <命令>         发给客户端探针（shot 名字、cmd 命令、slot n、look 水平 俯仰、lookat x y z、goto x z [秒]、use、sneak tick数、jump [tick数]、hit、lights [半径]、track on|off、rejoin 地址、attack、place、front [宽 高 左 上]、volume 0~1）
  g x z [秒]       走到 (x, z)，等到走到或走不到才接着往下（默认最多 30 秒）
  h x z [秒]       和 g 一样，但走到边上脚前面空了会起跳（跑酷）
  k <高度>         检查点：人已经比这个高度低 1.2 格以上（掉下去被接到更下面了），跳过到下一个 k 之前的步骤
  w <秒>           等
  l <文字> <秒>     等服务端日志出现文字（从上一次等到的那行之后算）
"""
import os
import sys
import time

root, script = sys.argv[1], sys.argv[2]
log = os.path.join(root, "server", "logs", "latest.log")


def read_log():
    with open(log, encoding="utf-8", errors="replace") as handle:
        return handle.readlines()


def append(path, text):
    with open(path, "a", encoding="utf-8") as handle:
        handle.write(text + "\n")


def feet_y():
    """等一行新的状态（探针一秒写一行），返回人脚下的高度。"""
    status = os.path.join(root, "game", "probe-status.csv")
    asked = time.time() * 1000
    for _ in range(30):
        try:
            with open(status, encoding="utf-8", errors="replace") as handle:
                rows = [r for r in handle.readlines() if r[:1].isdigit() and "," in r]
        except OSError:
            rows = []
        for r in reversed(rows):
            f = r.split(",")
            if len(f) > 5 and not f[1].startswith(("走", "准")):
                if float(f[0]) >= asked - 200:
                    return float(f[4])
                break
        time.sleep(0.1)
    return None


start = len(read_log())
mark = start
skipping = False
for raw in open(script, encoding="utf-8"):
    line = raw.strip()
    if not line:
        continue
    kind, _, rest = line.partition(" ")
    if kind == "k":
        y = feet_y()
        skipping = y is not None and y < float(rest) - 1.2
        if skipping:
            print(f"跳过 {rest} 这一处（人在 {y}）", flush=True)
        continue
    if skipping:
        continue
    if kind == "s":
        append(os.path.join(root, "ctl.txt"), rest)
        time.sleep(0.4)
    elif kind == "c":
        append(os.path.join(root, "game", "probe-commands.txt"), rest)
        time.sleep(0.2)
    elif kind in ("g", "h"):
        parts = rest.split()
        limit = float(parts[2]) if len(parts) > 2 else 30
        status = os.path.join(root, "game", "probe-status.csv")
        with open(status, encoding="utf-8", errors="replace") as handle:
            seen = len(handle.readlines())
        append(os.path.join(root, "game", "probe-commands.txt"), f"{'hop' if kind == 'h' else 'goto'} {parts[0]} {parts[1]} {int(limit)}")
        deadline = time.time() + limit + 3
        while time.time() < deadline:
            time.sleep(0.15)
            with open(status, encoding="utf-8", errors="replace") as handle:
                lines = handle.readlines()[seen:]
            if any("走到" in line or "走不到" in line for line in lines):
                break
    elif kind == "w":
        time.sleep(float(rest))
    elif kind == "l":
        text, _, seconds = rest.rpartition(" ")
        deadline = time.time() + float(seconds)
        found = None
        while found is None and time.time() < deadline:
            found = next((i for i, entry in enumerate(read_log()) if i >= mark and text in entry), None)
            if found is None:
                time.sleep(0.5)
        # 下一次等从这一行之后找，同一句话在一串步骤里可以出现多次。
        if found is not None:
            mark = found + 1
        print(("通过：" if found is not None else "超时：") + text)
print("---- 服务端日志 ----")
for entry in read_log()[start:]:
    print(entry.rstrip()[:200])
