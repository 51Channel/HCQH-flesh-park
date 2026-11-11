#!/usr/bin/env python3
"""肉的材质：颜色 + 法线（_n）+ 高光（_s），labPBR 格式，光影读得到。可以无缝平铺。
用法：生成肉材质.py <输出目录>（写到 输出目录/assets/fleshpark/textures/flesh/）
纯 Python，一张 512 要十几秒，只有改材质才要跑。
"""
import json
import math
import os
import struct
import sys
import zlib


def png(pixels, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(pixels[y * w * 4:(y + 1) * w * 4])

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b"")


def h2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def ss(a, b, x):
    k = max(0.0, min(1.0, (x - a) / (b - a)))
    return k * k * (3 - 2 * k)


def vn(u, v, pu, pv, seed):
    x, y = (u % 1) * pu, (v % 1) * pv
    ix, iy = int(x), int(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = h2(ix % pu, iy % pv, seed)
    b = h2((ix + 1) % pu, iy % pv, seed)
    c = h2(ix % pu, (iy + 1) % pv, seed)
    d = h2((ix + 1) % pu, (iy + 1) % pv, seed)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def fbm(u, v, pu, pv, seed, octaves):
    total, weight, amp = 0.0, 0.0, 1.0
    for o in range(octaves):
        total += vn(u, v, pu << o, pv << o, seed + o * 17) * amp
        weight += amp
        amp *= 0.5
    return total / weight


def ramp(stops, t):
    t = max(0.0, min(1.0, t)) * (len(stops) - 1)
    i = min(len(stops) - 2, int(t))
    k = t - i
    return tuple(stops[i][c] + (stops[i + 1][c] - stops[i][c]) * k for c in range(3))


# 湿肉的颜色其实很均匀：明暗交给光照，贴图上只有很小的起伏
BASE = {"wall": ((108, 36, 36), (172, 76, 66)), "rope": ((118, 38, 38), (186, 84, 72)), "dark": ((92, 30, 30), (142, 54, 48)), "pale": ((186, 142, 128), (226, 196, 176)), "vein": ((84, 26, 62), (128, 44, 90))}


def mix(a, b, k):
    return tuple(a[c] + (b[c] - a[c]) * k for c in range(3))


def surface(kind, seed, size):
    """返回高度、颜色、湿度。不分方向：肉上不能有条纹，也不能有斑点。"""
    heights = [0.0] * (size * size)
    colors = [None] * (size * size)
    wets = [0.0] * (size * size)
    low, high = BASE[kind]
    for y in range(size):
        v = y / size
        for x in range(size):
            u = x / size
            wu = u + (fbm(u, v, 3, 3, seed + 1, 2) - 0.5) * 0.14
            wv = v + (fbm(u, v, 3, 3, seed + 2, 2) - 0.5) * 0.14
            tone = ss(0.3, 0.7, fbm(u, v, 2, 2, seed + 3, 3))
            bump = fbm(wu, wv, 4, 4, seed + 4, 3)
            pore = fbm(wu, wv, 26, 26, seed + 5, 2)
            h = bump * 0.86 + pore * 0.14
            r, g, b = mix(low, high, tone * 0.72 + (bump - 0.5) * 0.4 + (pore - 0.5) * 0.12 + 0.14)
            # 薄膜：大片地淡淡泛粉
            sheen = ss(0.5, 0.78, fbm(u, v, 3, 3, seed + 9, 2)) * (0.08 if kind == "pale" else 0.16)
            r, g, b = mix((r, g, b), (206, 118, 104), sheen)
            # 血管：很细、比底色深一点点
            line = 1 - abs(2 * fbm(wu, wv, 5, 5, seed + 11, 3) - 1)
            vessel = ss(0.982, 0.998, line) * 0.16
            r, g, b = mix((r, g, b), (82, 16, 46), vessel)
            wet = ss(0.3, 0.65, fbm(u, v, 5, 5, seed + 12, 2))
            heights[y * size + x] = h
            colors[y * size + x] = (int(r), int(g), int(b))
            wets[y * size + x] = wet
    return heights, colors, wets


def write(target, name, kind, seed, size, strength):
    heights, colors, wets = surface(kind, seed, size)
    color, normal, spec = bytearray(size * size * 4), bytearray(size * size * 4), bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            i = y * size + x
            r, g, b = colors[i]
            color[i * 4:i * 4 + 4] = bytes((r, g, b, 255))
            dx = heights[y * size + (x + 1) % size] - heights[y * size + (x - 1) % size]
            dy = heights[((y + 1) % size) * size + x] - heights[((y - 1) % size) * size + x]
            nx, ny, nz = -dx * strength, -dy * strength, 1.0
            ln = math.sqrt(nx * nx + ny * ny + nz * nz)
            ao = 0.72 + 0.28 * ss(0.2, 0.55, heights[i])
            normal[i * 4:i * 4 + 4] = bytes((int((nx / ln * 0.5 + 0.5) * 255), int((ny / ln * 0.5 + 0.5) * 255), int(ao * 255), int(max(0.0, min(1.0, heights[i])) * 255)))
            smooth = 0.58 + 0.36 * wets[i]
            spec[i * 4:i * 4 + 4] = bytes((int(smooth * 255), 10, 200, 255))
    for suffix, data in (("", color), ("_n", normal), ("_s", spec)):
        with open(os.path.join(target, name + suffix + ".png"), "wb") as handle:
            handle.write(png(data, size, size))
    for suffix in ("", "_n", "_s"):
        with open(os.path.join(target, name + suffix + ".png.mcmeta"), "w") as handle:
            json.dump({"texture": {"blur": True}}, handle)
    print(name)


def main():
    target = os.path.join(sys.argv[1], "assets", "fleshpark", "textures", "flesh")
    os.makedirs(target, exist_ok=True)
    write(target, "wall", "wall", 31, 512, 60)
    write(target, "rope", "rope", 47, 512, 52)
    write(target, "dark", "dark", 59, 256, 30)
    write(target, "pale", "pale", 71, 256, 18)
    write(target, "vein", "vein", 83, 256, 18)


main()
