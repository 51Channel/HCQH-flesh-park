#!/usr/bin/env python3
"""光影用的噪声贴图：把 lib/noise.glsl 里格点上的随机数先算好存起来，光影里一次贴图采样就是一次平滑噪声，不用每个像素算八次哈希。
用法：生成噪声.py（写到 FleshPark/shaders/textures/）。3D 64³，2D 256²，单通道 8 位，四面都是循环的。"""
import json
import math
import os

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "FleshPark", "shaders", "textures")
os.makedirs(out, exist_ok=True)


def fract(v):
    return v - math.floor(v)


def hash31(x, y, z):
    px, py, pz = fract(x * 0.1031), fract(y * 0.1030), fract(z * 0.0973)
    d = px * (py + 33.33) + py * (pz + 33.33) + pz * (px + 33.33)
    px, py, pz = px + d, py + d, pz + d
    return fract((px + py) * pz)


def hash21(x, y):
    px, py = fract(x * 123.34), fract(y * 456.21)
    d = px * (px + 45.32) + py * (py + 45.32)
    px, py = px + d, py + d
    return fract(px * py)


def write(name, data):
    with open(os.path.join(out, name), "wb") as handle:
        handle.write(bytes(data))
    # 线性过滤、循环铺：Iris 从同名 .mcmeta 读
    with open(os.path.join(out, name + ".mcmeta"), "w") as handle:
        json.dump({"texture": {"blur": True, "clamp": False}}, handle)


n3 = 64
write("noise3d.dat", [min(255, int(hash31(x, y, z) * 256)) for z in range(n3) for y in range(n3) for x in range(n3)])
n2 = 256
write("noise2d.dat", [min(255, int(hash21(x, y) * 256)) for y in range(n2) for x in range(n2)])
print(out, n3 ** 3 + n2 ** 2, "bytes")
