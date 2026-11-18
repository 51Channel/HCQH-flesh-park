#!/usr/bin/env python3
"""生成血肉公园客户端模组的贴图和模型，写进 客户端模组/generated，再由 Gradle 和代码打成一个 jar。
用法：生成资源.py <输出目录>

- 肉的方块：直接换掉三种原版方块的贴图（这个实例专用，不影响别的实例）。
- 肉索：一节节鼓起来的肉块，网格在模组代码里，这里只生成贴图和物品定义。由插件摆成展示实体，插件能让它们蠕动。
- 每张肉贴图都带 labPBR 的 _n、_s，以后装光影包就有湿亮面和透光。
没有 PIL，PNG 自己写。
"""
import json
import math
import os
import shutil
import struct
import sys
import zlib

SIZE = 64


def png(pixels, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(pixels[y * w + x])

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def h2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def ss(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def vn(u, v, pu, pv, seed):
    """能无缝平铺的值噪声：u、v 在 0 到 1，pu、pv 是格子数。"""
    x, y = u * pu, v * pv
    ix, iy = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = h2(ix % pu, iy % pv, seed)
    b = h2((ix + 1) % pu, iy % pv, seed)
    c = h2(ix % pu, (iy + 1) % pv, seed)
    d = h2((ix + 1) % pu, (iy + 1) % pv, seed)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def fbm(u, v, pu, pv, seed, octaves=4):
    total, amp, norm = 0.0, 0.5, 0.0
    for i in range(octaves):
        total += vn(u, v, pu << i, pv << i, seed + i * 17) * amp
        norm += amp
        amp *= 0.5
    return total / norm


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    pos = t * (len(stops) - 1)
    i = min(int(pos), len(stops) - 2)
    k = pos - i
    return tuple(stops[i][c] + (stops[i + 1][c] - stops[i][c]) * k for c in range(3))


RAMPS = {
    "dark": [(24, 3, 7), (58, 9, 13), (104, 20, 22), (150, 44, 40)],
    "mid": [(52, 8, 12), (116, 22, 24), (172, 50, 46), (214, 104, 92)],
    "pale": [(92, 46, 44), (160, 104, 92), (208, 160, 140), (232, 204, 186)],
    "vein": [(70, 4, 8), (150, 12, 16), (214, 34, 30), (250, 96, 74)],
}


def worley(u, v, n, seed):
    """能平铺的细胞噪声：F1、F2 是到最近、次近种子点的距离，cell 是所在细胞的编号。肉的一个个小叶就是这样长的。"""
    x, y = u * n, v * n
    ix, iy = int(math.floor(x)), int(math.floor(y))
    f1, f2, cell = 9.0, 9.0, 0
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            cx, cy = (ix + ox) % n, (iy + oy) % n
            px = ix + ox + h2(cx, cy, seed)
            py = iy + oy + h2(cx, cy, seed + 7)
            d = math.hypot(px - x, py - y)
            if d < f1:
                f2, f1, cell = f1, d, cx * 97 + cy
            elif d < f2:
                f2 = d
    return f1, f2, cell


def flesh(kind, seed, fiber_u=False, size=SIZE):
    """
    肉不是花斑：一个个圆鼓的小叶挤在一起，小叶之间是一条很细的沟，表面有肌纤维和湿亮。颜色、血管、高光都跟着高度走。
    fiber_u：肉索用，肌纤维沿 U 方向拉长，小叶淡一些。白筋几乎全是纤维。
    """
    colors = RAMPS[kind]
    tendon = kind == "pale"
    lobes = 3 if fiber_u else 5
    lobe_weight = 0.12 if tendon else 0.14 if fiber_u else 0.5
    fib_weight = 0.72 if tendon else 0.14 if fiber_u else 0.18
    heights = [0.0] * (size * size)
    wets = [0.0] * (size * size)
    rgb = [None] * (size * size)
    for y in range(size):
        v = y / size
        for x in range(size):
            u = x / size
            # 先把坐标扭一下，小叶才不是规整的格子
            wu = (u + (fbm(u, v, 4, 4, seed + 11, 2) - 0.5) * 0.16) % 1.0
            wv = (v + (fbm(u, v, 4, 4, seed + 13, 2) - 0.5) * 0.16) % 1.0
            f1, f2, cell = worley(wu, wv, lobes, seed + 3)
            pillow = max(0.0, 1 - (f1 * 1.25) ** 2)
            crease = 1 - ss(0.0, 0.13, f2 - f1)
            tone = h2(cell, 1, seed + 21)
            blot = fbm(u, v, 3, 3, seed, 3)
            if fiber_u:
                fib = fbm(u, v, 2, 26, seed + 40, 3)
            elif tendon:
                fib = fbm(u, v, 3, 22, seed + 40, 3)
            else:
                fib = fbm(u, v, 10, 10, seed + 40, 3)
            vein = 1 - abs(2 * fbm(wu, wv, 4, 4, seed + 120, 3) - 1)
            vein = ss(0.955, 0.995, vein)
            height = pillow * lobe_weight + fib * fib_weight + blot * (1 - lobe_weight - fib_weight) + (tone - 0.5) * 0.12
            height -= crease * 0.12
            heights[y * size + x] = height
            wet = ss(0.38, 0.68, fbm(u, v, 4, 4, seed + 160, 2))
            wets[y * size + x] = wet
            r, g, b = ramp(colors, ss(0.2, 0.8, height))
            # 血管：细、发紫，只淡淡压一层
            vein_k = 0.12 if fiber_u else 0.45
            r, g, b = r + (64 - r) * vein * vein_k, g + (10 - g) * vein * vein_k, b + (34 - b) * vein * vein_k
            # 肉索的外形已经是真网格，贴图上的沟再明显就成了一块块裂开的皮，几乎不留
            k = 1 - crease * (0.08 if fiber_u else 0.25 if tendon else 0.46)
            r, g, b = r * k, g * k, b * k
            # 原版没有高光，湿亮面先画上去：小叶鼓起来的那一小块。有光影包时 _s 接手
            sheen = ss(0.72, 0.96, pillow) * wet * (0.5 + 0.5 * fib)
            sheen = max(sheen, ss(0.64, 0.8, fib) * wet * 0.22 if fiber_u else 0.0)
            r, g, b = r + (240 - r) * sheen * 0.34, g + (170 - g) * sheen * 0.34, b + (150 - b) * sheen * 0.34
            if h2(x, y, seed + 200) > 0.992 and pillow > 0.5:
                r, g, b = min(255, r + 60), min(255, g + 48), min(255, b + 42)
            rgb[y * size + x] = (int(r), int(g), int(b), 255)
    normal = [None] * (size * size)
    spec = [None] * (size * size)
    for y in range(size):
        for x in range(size):
            i = y * size + x
            dx = heights[y * size + (x + 1) % size] - heights[y * size + (x - 1) % size]
            dy = heights[((y + 1) % size) * size + x] - heights[((y - 1) % size) * size + x]
            nx, ny, nz = -dx * 5.0, -dy * 5.0, 1.0
            ln = math.sqrt(nx * nx + ny * ny + nz * nz)
            normal[i] = (int((nx / ln * 0.5 + 0.5) * 255), int((ny / ln * 0.5 + 0.5) * 255), 255, max(1, int(ss(0.1, 0.8, heights[i]) * 254)))
            # R 光滑度，G 反射率（水和肉差不多 F0=0.04），B 次表面散射，A=255 表示不发光
            spec[i] = (int(165 + 80 * wets[i]), 10, 205, 255)
    return png(rgb, size, size), png(normal, size, size), png(spec, size, size)


def solid(colors, w=16, h=16, seed=1, noise=10):
    """小物件的贴图：一个颜色加一点脏。colors 可以是函数 (x, y) -> (r, g, b)。"""
    out = []
    for y in range(h):
        for x in range(w):
            base = colors(x, y) if callable(colors) else colors
            n = (h2(x, y, seed) - 0.5) * noise
            out.append((max(0, min(255, int(base[0] + n))), max(0, min(255, int(base[1] + n))), max(0, min(255, int(base[2] + n))), 255))
    return png(out, w, h)


def dirty_glass(seed, size=64, alpha=(10, 26)):
    """脏玻璃：几乎透明，只有淡淡的水痕。对比度一高，放大到整扇窗就是一块块方斑。"""
    out = []
    for y in range(size):
        v = y / size
        for x in range(size):
            u = x / size
            n = fbm(u, v, 3, 3, seed, 2)
            a = alpha[0] + (alpha[1] - alpha[0]) * ss(0.3, 0.75, n)
            out.append((168, 164, 156, int(a)))
    return png(out, size, size)


def soft_puff(seed, size=16):
    """窗上的水汽：纯色，透明度由模组里的顶点色给，放大也没有格子。"""
    return png([(128, 134, 132, 255)] * (size * size), size, size)


def surface_maps(paint, size=128, strength=3.0):
    """高清方块贴图和它的法线、光滑度。paint(u, v) 返回 (r, g, b, 高度, 光滑度)，一格 1 米，128 像素够近看。"""
    color, normal, spec, heights = [], [], [], []
    samples = [paint(x / size, y / size) for y in range(size) for x in range(size)]
    for s in samples:
        color.append((clamp8(s[0]), clamp8(s[1]), clamp8(s[2]), 255))
        heights.append(s[3])
    for y in range(size):
        for x in range(size):
            dx = heights[y * size + (x + 1) % size] - heights[y * size + (x - 1) % size]
            dy = heights[((y + 1) % size) * size + x] - heights[((y - 1) % size) * size + x]
            nx, ny, nz = -dx * strength * size / 16, -dy * strength * size / 16, 1.0
            ln = math.sqrt(nx * nx + ny * ny + nz * nz)
            normal.append((clamp8((nx / ln * 0.5 + 0.5) * 255), clamp8((ny / ln * 0.5 + 0.5) * 255), clamp8((0.7 + 0.3 * ss(0.0, 0.6, heights[y * size + x])) * 255), 250))
            spec.append((clamp8(samples[y * size + x][4] * 255), 10, 200, 255))
    return png(color, size, size), png(normal, size, size), png(spec, size, size)


def clamp8(v):
    return max(0, min(255, int(v)))


def stain(u, v, seed):
    """贴图上只留极淡的斑。污渍画进贴图会按方块重复成豹纹，大片的脏由光影按世界坐标加。"""
    return ss(0.5, 0.78, fbm(u, v, 3, 2, seed, 3)) * 0.12


def seam(u, v, per_u, per_v, width):
    """缝：0 在缝里，1 在砖面上。"""
    du = abs(((u * per_u + 0.5) % 1) - 0.5) / per_u
    dv = abs(((v * per_v + 0.5) % 1) - 0.5) / per_v
    return ss(0, width, min(du, dv))


def terrazzo(base, seed):
    """水磨石：大小不一的圆石子，不是一格一格的方块。"""
    def paint(u, v):
        r, g, b = base
        k = fbm(u, v, 4, 4, seed + 1, 2)
        r, g, b = r * (0.94 + 0.1 * k), g * (0.94 + 0.1 * k), b * (0.94 + 0.1 * k)
        for layer, grid in enumerate((9, 17, 31)):
            cx, cy = int(u * grid), int(v * grid)
            for ox in (-1, 0, 1):
                for oy in (-1, 0, 1):
                    gx, gy = (cx + ox) % grid, (cy + oy) % grid
                    sd = seed + layer * 13
                    if h2(gx, gy, sd) < 0.45:
                        continue
                    px = (cx + ox + 0.2 + 0.6 * h2(gx, gy, sd + 1)) / grid
                    py = (cy + oy + 0.2 + 0.6 * h2(gx, gy, sd + 2)) / grid
                    rad = (0.18 + 0.3 * h2(gx, gy, sd + 3)) / grid
                    d = math.hypot(u - px, v - py)
                    inside = 1 - ss(rad * 0.75, rad, d)
                    if inside > 0:
                        shade = 54 + 190 * h2(gx, gy, sd + 4)
                        m = inside * 0.42
                        r, g, b = r + (shade - r) * m, g + (shade * 0.97 - g) * m, b + (shade * 0.9 - b) * m
        edge = seam(u, v, 1, 1, 0.01)
        return (r * (0.5 + 0.5 * edge), g * (0.5 + 0.5 * edge), b * (0.5 + 0.5 * edge), 0.4 + 0.6 * edge, 0.2 * edge)
    return paint


def tile(base, seed, per_u=4, per_v=4):
    def paint(u, v):
        edge = seam(u, v, per_u, per_v, 0.008)
        cell = h2(int(u * per_u), int(v * per_v), seed)
        k = 0.96 + cell * 0.07
        grain = fbm(u, v, 12, 12, seed + 1, 2)
        dirt = stain(u, v, seed + 2) * (0.25 + 0.55 * (1 - edge))
        f = k * (0.98 + grain * 0.04) * (1 - dirt) * (0.42 + 0.58 * edge)
        return (base[0] * f, base[1] * f, base[2] * f, 0.3 + 0.7 * edge, (0.4 - dirt * 0.3) * edge)
    return paint


def plain(base, seed, gloss_level, wear=0.3):
    """抹灰、水泥、喷漆铁：没有纹理，只有很软的斑和一点细颗粒。"""
    def paint(u, v):
        k = fbm(u, v, 3, 3, seed, 3)
        grain = fbm(u, v, 40, 40, seed + 1, 1)
        dirt = stain(u, v, seed + 2) * wear
        f = (0.92 + 0.14 * k) * (0.985 + 0.03 * grain) * (1 - dirt)
        return (base[0] * f, base[1] * f, base[2] * f, 0.5 + (k - 0.5) * 0.12 + (grain - 0.5) * 0.04, gloss_level * (1 - dirt))
    return paint


def planks(base, seed, boards=4):
    def paint(u, v):
        row = int(v * boards)
        phase = h2(row, 3, seed) * 9
        grain = fbm((u + phase) % 1, v, 2, 44, seed + 1, 3)
        knot = fbm((u + phase) % 1, v, 3, 3, seed + 4, 2)
        tone = 0.74 + 0.5 * h2(row, 7, seed)
        edge = ss(0, 0.01, abs(((v * boards + 0.5) % 1) - 0.5) / boards)
        butt = (u + h2(row, 11, seed)) % 1
        edge *= ss(0, 0.006, min(butt, 1 - butt))
        f = tone * (0.74 + 0.5 * grain) * (0.85 + 0.3 * knot) * (0.32 + 0.68 * edge)
        return (base[0] * f, base[1] * f, base[2] * f, 0.3 + 0.2 * grain + 0.5 * edge, 0.42 * edge * (0.6 + 0.7 * knot))
    return paint


def enamel(base, seed):
    """储物柜的旧搪瓷：一片颜色，边上磨掉一点，竖着的划痕。"""
    def paint(u, v):
        k = fbm(u, v, 3, 2, seed, 3)
        scratch = ss(0.8, 0.9, fbm(u, v, 48, 3, seed + 1, 2))
        rust = ss(0.7, 0.82, fbm(u, v, 9, 6, seed + 3, 3)) * 0.8
        f = 0.9 + 0.16 * k
        r, g, b = base[0] * f, base[1] * f, base[2] * f
        r, g, b = r + (150 - r) * scratch * 0.18, g + (150 - g) * scratch * 0.18, b + (140 - b) * scratch * 0.18
        r, g, b = r + (94 - r) * rust, g + (58 - g) * rust, b + (38 - b) * rust
        return (r, g, b, 0.55 - scratch * 0.08 - rust * 0.15, 0.62 * (1 - rust))
    return paint


def steel_plate(base, seed):
    """外壳：一块块喷漆钢板，只有一条细缝，不是瓷砖。"""
    def paint(u, v):
        edge = seam(u, v, 1, 1, 0.012)
        k = fbm(u, v, 3, 3, seed, 3)
        f = (0.86 + 0.26 * k) * (0.45 + 0.55 * edge)
        return (base[0] * f, base[1] * f, base[2] * f, 0.3 + 0.7 * edge, 0.42 * edge)
    return paint


def lit_window(seed):
    """亮着灯的窗：上面亮、下面暗，一排顶灯，中间一根窗框，玻璃上有脏。"""
    def paint(u, v):
        glow = 0.2 + 0.8 * (1 - v) ** 1.6
        lamp = math.exp(-((v - 0.14) / 0.035) ** 2) * (0.55 + 0.45 * math.cos(u * 6.2832 * 3))
        dirt = 0.72 + 0.28 * fbm(u, v, 5, 7, seed, 3)
        r, g, b = 226 * glow * dirt, 128 * glow * dirt, 44 * glow * dirt
        r, g, b = r + 60 * lamp, g + 90 * lamp, b + 90 * lamp
        frame = seam(u, v, 2, 1, 0.022)
        outer = ss(0, 0.03, min(u, 1 - u, v, 1 - v))
        f = frame * outer
        return (r * f + 12 * (1 - f), g * f + 12 * (1 - f), b * f + 14 * (1 - f), 0.5, 0.3)
    return paint


def brushed(base, seed):
    def paint(u, v):
        streak = fbm(u, v, 2, 64, seed, 2)
        k = fbm(u, v, 3, 3, seed + 1, 2)
        dirt = stain(u, v, seed + 2) * 0.4
        f = (0.95 + 0.1 * streak) * (0.95 + 0.1 * k) * (1 - dirt)
        return (base[0] * f, base[1] * f, base[2] * f, 0.5 + (streak - 0.5) * 0.03, 0.6 * (1 - dirt))
    return paint


def face(texture, uv=(0, 0, 16, 16)):
    return {"uv": list(uv), "texture": texture}


def box(a, b, texture="#t", rotation=None, faces=None):
    element = {"from": list(a), "to": list(b), "faces": {n: face(texture) for n in ("north", "south", "east", "west", "up", "down")}}
    if faces:
        element["faces"].update(faces)
    if rotation is not None:
        element["rotation"] = rotation
    return element


def flashlight_base():
    """手电的网格在模组代码里，这个模型只提供拿在手里的位置。"""
    return {
        "textures": {"particle": "fleshpark:item/flashlight"},
        "gui_light": "front",
        "display": {
            "firstperson_righthand": {"rotation": [6, -12, 0], "translation": [5.5, 1.3, -4], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [6, -12, 0], "translation": [5.5, 1.3, -4], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
            "gui": {"rotation": [30, 225, 0], "scale": [0.7, 0.7, 0.7]},
        },
    }


def special_item(base, model):
    return {"model": {"type": "minecraft:special", "base": base, "model": model}}


def gloss(seed, size=128):
    """湿亮面的遮罩：一片片连着的亮斑，高光才是碎的，不是塑料上一整条。"""
    pixels = []
    for y in range(size):
        for x in range(size):
            u, v = x / size, y / size
            streak = fbm(u * 3, v * 9, 3, 9, seed, 3)
            spot = fbm(u * 11, v * 11, 11, 11, seed + 7, 2)
            k = ss(0.42, 0.62, streak * 0.65 + spot * 0.35)
            pixels.append((255, 255, 255, int(255 * (0.18 + 0.82 * k))))
    return png(pixels, size, size)


def item_definition(name):
    return {"model": {"type": "minecraft:model", "model": "fleshpark:item/" + name}}


def tex(paint, size=128):
    """一张贴图：paint(u, v) 返回 (r, g, b) 或 (r, g, b, a)，u、v 在 0 到 1，能平铺。"""
    out = []
    for y in range(size):
        for x in range(size):
            c = paint(x / size, y / size)
            out.append((clamp8(c[0]), clamp8(c[1]), clamp8(c[2]), clamp8(c[3]) if len(c) > 3 else 255))
    return png(out, size, size)


def mix3(a, b, k):
    return (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k)


def steel_textures():
    """深井钢结构（插件 Rig）的贴图：锈钢、黄漆、花纹钢板、格栅、磨石子、混凝土、镀锌风管、灯箱面板，还有锈透了的花纹钢板、漆掉光了的扶手。一张盖 1 到 2 格，按世界坐标铺。"""
    t = {}

    def rust(u, v):
        k = fbm(u, v, 4, 4, 601, 4)
        flake = ss(0.55, 0.75, fbm(u, v, 12, 12, 603, 3))
        streak = ss(0.45, 0.8, fbm(u, v, 24, 3, 605, 3))
        pit = ss(0.82, 0.9, fbm(u, v, 32, 32, 607, 2))
        c = mix3((58, 50, 46), (112, 58, 30), ss(0.35, 0.7, k))
        c = mix3(c, (150, 82, 38), flake * 0.55)
        c = mix3(c, (40, 30, 26), streak * 0.35 + pit * 0.5)
        return c
    t["rust"] = tex(rust)

    def paint(u, v):
        chip = ss(0.6, 0.66, fbm(u, v, 8, 8, 611, 4))
        worn = ss(0.5, 0.8, fbm(u, v, 3, 3, 613, 3))
        dirt = ss(0.4, 0.9, fbm(u, v, 6, 16, 615, 3))
        c = mix3((204, 158, 34), (150, 116, 28), worn * 0.6)
        c = mix3(c, (78, 66, 52), dirt * 0.35)
        c = mix3(c, rust(u, v), chip)
        return c
    t["paint"] = tex(paint)

    def tread(u, v):
        # 花纹钢板：一格 6 × 6 个凸起的长条，一行横一行竖
        n = 6
        x, y = u * n, v * n
        ix, iy = int(x), int(y)
        fx, fy = x - ix - 0.5, y - iy - 0.5
        if (ix + iy) % 2:
            fx, fy = fy, fx
        a, b = (fx + fy) * 0.7071, (fx - fy) * 0.7071
        bump = 1 - ss(0.9, 1.05, math.hypot(a / 0.4, b / 0.09))
        k = fbm(u, v, 4, 4, 621, 3)
        worn = ss(0.45, 0.85, fbm(u, v, 3, 3, 623, 3))
        groove = ss(0.62, 0.8, fbm(u, v, 10, 10, 625, 3))
        c = mix3((74, 76, 78), (98, 98, 96), k)
        c = mix3(c, (150, 150, 146), bump * (0.45 + 0.4 * worn))
        c = mix3(c, (104, 60, 34), groove * (1 - bump) * 0.7)
        return c
    t["tread"] = tex(tread)

    def tread_old(u, v):
        # 用了很多年的花纹钢板：凸条磨平了一半，锈从凹槽里漫出来连成片，有几块掉漆露出底下的红丹，边上一道道污水印
        c = tread(u, v)
        crust = ss(0.4, 0.7, fbm(u, v, 3, 3, 627, 4))
        flake = ss(0.6, 0.72, fbm(u, v, 14, 14, 629, 3))
        primer = ss(0.72, 0.78, fbm(u, v, 5, 5, 631, 3))
        stain = ss(0.5, 0.85, fbm(u, v, 2, 10, 633, 3))
        c = mix3(c, rust(u, v), crust * 0.85)
        c = mix3(c, (150, 78, 36), flake * crust * 0.5)
        c = mix3(c, (128, 52, 34), primer * 0.7)
        return mix3(c, (44, 34, 30), stain * 0.4)
    t["tread_old"] = tex(tread_old)

    def paint_old(u, v):
        # 漆掉得差不多了的扶手：黄漆只剩一块一块，底下是锈和手摸黑的地方
        keep = ss(0.5, 0.6, fbm(u, v, 9, 9, 617, 4))
        grime = ss(0.45, 0.8, fbm(u, v, 8, 2, 619, 3))
        c = mix3(rust(u, v), mix3(paint(u, v), (110, 92, 52), 0.4), keep * 0.7)
        return mix3(c, (36, 30, 26), grime * 0.45)
    t["paint_old"] = tex(paint_old)

    def grate(u, v):
        # 格栅：竖着的承重扁钢一格 8 根，横着的连杆一格 3 根，空当透明
        bx = abs(((u * 8) % 1) - 0.5)
        by = abs(((v * 3) % 1) - 0.5)
        solid_ = bx > 0.38 or by > 0.44 or u < 0.02 or u > 0.98
        c = mix3((70, 70, 68), (110, 62, 34), ss(0.4, 0.75, fbm(u, v, 5, 5, 631, 3)))
        c = mix3(c, (120, 120, 116), 0.3 if bx > 0.44 else 0)
        return (c[0], c[1], c[2], 255 if solid_ else 0)
    t["grate"] = tex(grate)

    tz = terrazzo((176, 168, 152), 641)

    def terrazzo_(u, v):
        r, g, b, _, _ = tz(u, v)
        dirt = ss(0.45, 0.85, fbm(u, v, 4, 4, 643, 3))
        return mix3((r, g, b), (70, 58, 50), dirt * 0.45)
    t["terrazzo"] = tex(terrazzo_)

    def concrete(u, v):
        k = fbm(u, v, 6, 6, 651, 4)
        stone = h2(int(u * 64), int(v * 64), 653)
        stain = ss(0.5, 0.85, fbm(u, v, 3, 8, 655, 3))
        c = mix3((92, 90, 86), (138, 134, 126), k)
        if stone > 0.86:
            c = mix3(c, (170, 164, 150), 0.6)
        return mix3(c, (54, 44, 40), stain * 0.5)
    t["concrete"] = tex(concrete)

    def duct(u, v):
        f1, f2, cell = worley(u, v, 10, 661)
        spangle = 0.85 + 0.25 * h2(cell, 3, 663)
        seam_ = ss(0.0, 0.015, abs(((v * 1) % 1) - 0.5) - 0.47)
        dirt = ss(0.45, 0.85, fbm(u, v, 4, 4, 665, 3))
        rust_ = ss(0.7, 0.85, fbm(u, v, 8, 8, 667, 3))
        g = 150 * spangle
        c = (g, g * 1.01, g * 0.98)
        c = mix3(c, (60, 60, 58), dirt * 0.55)
        c = mix3(c, (120, 66, 36), rust_ * 0.6)
        c = mix3(c, (40, 40, 40), seam_ * 0.5)
        return c
    t["duct"] = tex(duct)

    def sign(u, v):
        # 灯箱面板：灰白底，中间一道墨绿的条，条上一个白箭头往前指；面板碎了，一道道裂纹
        c = (160, 156, 140)
        if 0.36 < v < 0.64:
            c = (34, 74, 58)
            ax, ay = u - 0.5, v - 0.5
            shaft = abs(ay) < 0.035 and -0.25 < ax < 0.08
            head = 0.06 < ax + 0.0 < 0.22 and abs(ay) < (0.22 - ax) * 0.75
            if shaft or head:
                c = (186, 184, 170)
        crack = ss(0.0, 0.012, 0.012 - abs(fbm(u, v, 5, 3, 671, 3) - 0.5))
        dirt = ss(0.4, 0.9, fbm(u, v, 4, 4, 673, 3))
        c = mix3(c, (60, 54, 46), dirt * 0.35)
        return mix3(c, (20, 18, 16), crack * 0.9)
    t["sign"] = tex(sign, 256)
    return t


def main():
    target = sys.argv[1]
    files = {}

    def add_flesh(path, kind, seed, fiber_u=False, size=SIZE):
        color, normal, specular = flesh(kind, seed, fiber_u, size)
        files[path + ".png"] = color
        files[path + "_n.png"] = normal
        files[path + "_s.png"] = specular

    # 看得到的建筑材质全换成高清的，近看不是一格一格的马赛克
    for name, paint, strength in (
        ("calcite", terrazzo((196, 190, 178), 301), 2.0),
        ("polished_diorite", terrazzo((142, 138, 130), 311), 2.0),
        ("polished_andesite", plain((116, 116, 114), 321, 0.42, 0.45), 0.5),
        ("white_terracotta", tile((204, 198, 182), 331), 3.0),
        ("light_gray_terracotta", tile((150, 146, 136), 341), 3.0),
        ("cyan_terracotta", tile((70, 112, 112), 351, 4, 2), 3.0),
        ("smooth_quartz", plain((138, 132, 120), 361, 0.1, 0.5), 0.35),
        ("white_concrete", plain((180, 176, 166), 371, 0.18, 0.42), 0.35),
        ("light_gray_concrete", plain((134, 134, 130), 381, 0.18, 0.42), 0.35),
        ("gray_concrete", plain((74, 76, 78), 391, 0.2, 0.42), 0.35),
        ("polished_deepslate", plain((50, 52, 56), 401, 0.5, 0.35), 0.35),
        ("oxidized_copper", enamel((62, 108, 98), 411), 2.0),
        ("dark_oak_planks", planks((88, 62, 42), 421), 3.0),
        ("spruce_planks", planks((122, 90, 60), 431), 3.0),
        ("iron_block", brushed((120, 122, 122), 441), 0.15),
        ("weathered_copper", steel_plate((62, 72, 72), 451), 2.0),
        ("weathered_cut_copper", steel_plate((44, 50, 52), 461), 2.0),
        ("shroomlight", lit_window(471), 0.2),
    ):
        color, normal, specular = surface_maps(paint, 128, strength)
        files["assets/minecraft/textures/block/" + name + ".png"] = color
        files["assets/minecraft/textures/block/" + name + "_n.png"] = normal
        files["assets/minecraft/textures/block/" + name + "_s.png"] = specular
    # 三种肉方块：换原版贴图
    add_flesh("assets/minecraft/textures/block/nether_wart_block", "dark", 11)
    add_flesh("assets/minecraft/textures/block/red_mushroom_block", "mid", 23)
    add_flesh("assets/minecraft/textures/block/mushroom_stem", "pale", 37)
    # 肉墙不能一眼看出方格重复：每格随机转个方向
    variants = [{"model": "minecraft:block/nether_wart_block"}]
    for x, y in ((0, 90), (0, 180), (0, 270), (90, 0), (90, 90), (90, 180), (90, 270), (180, 0), (180, 90), (270, 0), (270, 180)):
        variant = {"model": "minecraft:block/nether_wart_block"}
        if x:
            variant["x"] = x
        if y:
            variant["y"] = y
        variants.append(variant)
    files["assets/minecraft/blockstates/nether_wart_block.json"] = json.dumps({"variants": {"": variants}}).encode()

    # 玻璃：原版玻璃的白色窗格看着像木格子窗。半透明只有染色玻璃支持，所以换两种染色玻璃当脏玻璃用
    files["assets/minecraft/textures/block/white_stained_glass.png"] = dirty_glass(5, 64, (14, 34))
    files["assets/minecraft/textures/block/white_stained_glass_pane_top.png"] = dirty_glass(6, 16)
    files["assets/minecraft/textures/block/light_gray_stained_glass.png"] = dirty_glass(8)

    # 肉索的几种肉块。血管的亮度由插件按心跳控制
    files["assets/fleshpark/models/item/lump_base.json"] = json.dumps({"textures": {"particle": "fleshpark:item/flesh_lump"}}).encode()
    files["assets/fleshpark/textures/item/flesh_gloss.png"] = gloss(131)
    for name, kind, seed in (("flesh_lump", "mid", 51), ("flesh_lump_dark", "dark", 63), ("flesh_lump_pale", "pale", 79), ("flesh_vein", "vein", 97)):
        files[f"assets/fleshpark/textures/item/{name}.png"] = flesh(kind, seed, True, 256)[0]
        # 近处直接铺这张图，要平滑过滤，不然贴到眼前是马赛克。进图集的那份不看这个设置
        files[f"assets/fleshpark/textures/item/{name}.png.mcmeta"] = json.dumps({"texture": {"blur": True}}).encode()
        model = {"type": "fleshpark:lump", "sprite": "fleshpark:item/" + name}
        if kind == "vein":
            model["glows"] = True
        files[f"assets/fleshpark/items/{name}.json"] = json.dumps(special_item("fleshpark:item/lump_base", model)).encode()

    # 灰色人形
    files["assets/fleshpark/textures/item/grey_skin.png"] = solid((104, 106, 108), 32, 32, 3, 5)

    def grey_face(x, y):
        # 没有五官，只有眼窝的位置浅浅凹下去
        ex = min(abs(x - 10), abs(x - 21))
        dent = max(0.0, 1 - math.hypot(ex / 4.5, (y - 14) / 3.5))
        return tuple(int(c * (1 - 0.3 * dent)) for c in (108, 110, 112))

    files["assets/fleshpark/textures/item/grey_face.png"] = solid(grey_face, 32, 32, 5, 14)
    for name, pose in (("grey_figure", "stand"), ("grey_figure_raised", "raised"), ("grey_bust", "bust"), ("grey_self", "self"), ("grey_staff", "staff"), ("grey_copy", "copy"),
                       ("grey_mirror", "mirror"), ("grey_you", "you")):
        files[f"assets/fleshpark/items/{name}.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:figure", "pose": pose})).encode()
    # 衣服的布：浅色，乘上颜色才是原来的颜色
    files["assets/fleshpark/textures/item/cloth.png"] = solid((226, 224, 220), 32, 32, 7, 16)
    # 每个人形都长得不一样：身份 × 编号 × 姿势。物品叫 fig_身份_编号（站着）或 fig_身份_编号_姿势
    figures = {
        "tourist": (60, ("stand", "sit", "clap", "reach", "raised", "slump")),
        "bather": (40, ("bust", "stand")),
        "kid": (10, ("stand", "sit", "clap")),
        "ranger": (6, ("stand", "reach", "slump")),
        "guide": (6, ("stand", "sit", "clap")),
        "cook": (6, ("stand", "sit", "clap", "slump")),
        "miner": (20, ("stand", "reach", "slump")),
        "rescue": (5, ("stand", "sit", "reach", "shake", "pull")),
        "tester": (11, ("stand", "sit", "clap", "reach")),
        "you": (1, ("stand", "sit", "clap")),
        "announcer": (2, ("sit", "stand")),
    }
    for kind, (count, poses) in figures.items():
        for n in range(count):
            for pose in poses:
                name = f"fig_{kind}_{n}" + ("" if pose == "stand" else f"_{pose}")
                files[f"assets/fleshpark/items/{name}.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:figure", "pose": f"{kind}.{n}.{pose}"})).encode()

    # 灰色的手：只在结局的第一人称里看到
    files["assets/fleshpark/models/item/hand_base.json"] = json.dumps({
        "textures": {"particle": "fleshpark:item/grey_skin"},
        "display": {
            "firstperson_righthand": {"rotation": [-18, -16, 6], "translation": [-3.0, 1.5, -6], "scale": [1.1, 1.1, 1.1]},
            "firstperson_lefthand": {"rotation": [-18, 16, -6], "translation": [3.0, 1.5, -6], "scale": [1.1, 1.1, 1.1]},
        },
    }).encode()
    files["assets/fleshpark/items/grey_hand.json"] = json.dumps(special_item("fleshpark:item/hand_base", {"type": "fleshpark:hand"})).encode()

    # 换班：方向盘，开园前夜公路上的车队和烟花（顶点都在模组代码里）
    # 麻醉科：镇静剂总阀的手轮。运营中心：监控屏上你自己的脸（用本机皮肤）
    for name in ("wheel", "road", "valve", "face"):
        files[f"assets/fleshpark/models/item/{name}_base.json"] = json.dumps({"textures": {"particle": "fleshpark:item/flashlight"}}).encode()
        files[f"assets/fleshpark/items/{name}.json"] = json.dumps(special_item(f"fleshpark:item/{name}_base", {"type": f"fleshpark:{name}"})).encode()
    # 结尾车上的电视：一路上截下来的画面，第几张（截图在模组内存里，没截到就放雪花）
    for i in range(8):
        files[f"assets/fleshpark/items/shot_{i}.json"] = json.dumps(special_item("fleshpark:item/face_base", {"type": "fleshpark:shot", "index": i})).encode()

    # 液面、腺泡内壁、腺泡洞口
    for name, color in (("pool_thin", 0xC08A3A3E), ("pool_mid", 0xD06A2228), ("pool_thick", 0xE0481014), ("pool_water", 0xC8241E1A)):
        files[f"assets/fleshpark/items/{name}.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:pool", "color": color - (1 << 32)})).encode()
    files["assets/fleshpark/items/burrow.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:burrow"})).encode()
    files["assets/fleshpark/items/grape.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:grape"})).encode()

    # 海报：贴图在 生成海报.swift 里画，这里只有一张薄片模型，插件靠缩放定大小
    for name in ("poster_park", "poster_survived", "poster_heartthrob", "poster_closed", "poster_map", "poster_reopen", "poster_new", "poster_ticket", "poster_july4", "poster_rating", "tv_1", "tv_2", "tv_3", "tv_4", "tv_5", "tv_6", "tv_static", "tv_off", "tv_rate", "ride_photo", "handprints") + tuple(f"tester_photo_{n}" for n in range(1, 11)):
        files[f"assets/fleshpark/models/item/{name}.json"] = json.dumps({
            "textures": {"t": "fleshpark:item/" + name, "particle": "fleshpark:item/" + name},
            "elements": [{"from": [0, 0, 7.9], "to": [16, 16, 8.1], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#t"}, "north": {"uv": [16, 0, 0, 16], "texture": "#t"}}}],
        }).encode()
        files[f"assets/fleshpark/items/{name}.json"] = json.dumps(item_definition(name)).encode()

    # 肉的网格：一个物品定义，网格在模组里按编号取
    files["assets/fleshpark/items/flesh_mesh.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:mesh"})).encode()

    # 深井的钢结构和残骸：网格在模组里按编号取，贴图按材质分
    files["assets/fleshpark/items/steel_mesh.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:steel"})).encode()
    for name, data in steel_textures().items():
        files["assets/fleshpark/textures/steel/" + name + ".png"] = data
    # 深井里半格的落脚处：看不见的铜台阶（插件的 B.FOOT_HALF）。换成一个没有面的模型，碰撞还在
    files["assets/fleshpark/models/block/empty.json"] = json.dumps({"textures": {"particle": "minecraft:block/weathered_cut_copper"}, "elements": []}).encode()
    files["assets/minecraft/blockstates/waxed_weathered_cut_copper_slab.json"] = json.dumps({"variants": {k: {"model": "fleshpark:block/empty"} for k in ("type=bottom", "type=top", "type=double")}}).encode()

    # 缆车：车身的模型在模组代码里，贴图是旧油漆
    def tram_paint(x, y):
        v = y / 256
        u = x / 256
        rust = ss(0.62, 0.8, fbm(u, v, 5, 5, 201, 3))
        streak = ss(0.55, 0.85, fbm(u, v, 18, 3, 203, 2))
        k = 1 - 0.28 * rust - 0.14 * streak
        return (int(226 * k + 34 * rust), int(226 * k + 8 * rust), int(222 * k - 6 * rust))

    files["assets/fleshpark/textures/item/mist_puff.png"] = soft_puff(41)
    files["assets/fleshpark/items/mist_puff.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:mist"})).encode()
    files["assets/fleshpark/textures/item/tram_paint.png"] = solid(tram_paint, 256, 256, 15, 8)
    files["assets/fleshpark/items/tram_body.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:tram"})).encode()

    # 管线：颜色在模组代码里按顶点染
    files["assets/fleshpark/textures/item/pipe.png"] = solid((205, 205, 205), 16, 16, 11, 12)
    for name, color in (("pipe_yellow", 0xFFD8A81E), ("pipe_blue", 0xFF2A6CB0), ("pipe_grey", 0xFF85888C)):
        files[f"assets/fleshpark/items/{name}.json"] = json.dumps(special_item("fleshpark:item/lump_base", {"type": "fleshpark:pipe", "color": color - (1 << 32)})).encode()

    # 手电
    # 手电的颜色由顶点色给，贴图只是一层浅色的拉丝纹
    files["assets/fleshpark/textures/item/flashlight.png"] = solid((228, 229, 232), 16, 16, 7, 18)
    files["assets/fleshpark/textures/item/flashlight_lens.png"] = solid((250, 240, 214), 16, 16, 9, 6)
    files["assets/fleshpark/models/item/flashlight_base.json"] = json.dumps(flashlight_base()).encode()
    files["assets/fleshpark/items/flashlight.json"] = json.dumps(special_item("fleshpark:item/flashlight_base", {"type": "fleshpark:flashlight"})).encode()

    shutil.rmtree(target, ignore_errors=True)
    for name, data in files.items():
        path = os.path.join(target, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as handle:
            handle.write(data)
    print(f"{target}：{len(files)} 个文件")


main()
