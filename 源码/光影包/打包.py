#!/usr/bin/env python3
"""把光影包打成 zip，放进客户端模组。用法：打包.py <输出 zip>。模组启动时会把它放到 shaderpacks 并启用。"""
import os
import sys
import zipfile

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "FleshPark")
target = sys.argv[1]
os.makedirs(os.path.dirname(target), exist_ok=True)
with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as pack:
    for folder, _, names in sorted(os.walk(root)):
        for name in sorted(names):
            path = os.path.join(folder, name)
            info = zipfile.ZipInfo(os.path.relpath(path, root), date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            with open(path, "rb") as handle:
                pack.writestr(info, handle.read())
print(target, os.path.getsize(target))
