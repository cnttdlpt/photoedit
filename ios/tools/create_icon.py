#!/usr/bin/env python3
"""Rasterize the existing Android icon geometry for iOS; no new artwork/dependencies."""
import json
import math
import struct
import subprocess
import zlib
from pathlib import Path

root = Path(__file__).resolve().parents[2]
size = 2048
scale = size / 108
canvas = bytearray(bytes.fromhex('17251F') * size * size)


def run(y, x1, x2, color):
    x1, x2 = max(0, int(math.ceil(x1))), min(size, int(math.ceil(x2)))
    if x2 > x1 and 0 <= y < size:
        start = (y * size + x1) * 3
        canvas[start:start + (x2 - x1) * 3] = color * (x2 - x1)


def polygon(points, color):
    points = [(x * scale, y * scale) for x, y in points]
    for y in range(max(0, int(min(p[1] for p in points))), min(size, int(max(p[1] for p in points)) + 1)):
        scan = y + 0.5
        cuts = []
        for i, (x1, y1) in enumerate(points):
            x2, y2 = points[(i + 1) % len(points)]
            if min(y1, y2) <= scan < max(y1, y2):
                cuts.append(x1 + (scan - y1) * (x2 - x1) / (y2 - y1))
        cuts.sort()
        for i in range(0, len(cuts), 2):
            run(y, cuts[i], cuts[i + 1], color)


for shape in json.loads((root / 'android/design/icon-geometry.json').read_text()):
    polygon(shape['points'], bytes.fromhex(shape['color'][1:]))
ink = bytes.fromhex('F4F1DE')
polygon([(51, 53), (57, 53), (57, 66), (51, 66)], ink)
for y in range(int(43 * scale), int(49 * scale) + 1):
    dy = (y + 0.5) / scale - 46
    if abs(dy) <= 3:
        dx = math.sqrt(9 - dy * dy) * scale
        run(y, 54 * scale - dx, 54 * scale + dx, ink)


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)


pixels = b''.join(b'\x00' + canvas[y * size * 3:(y + 1) * size * 3] for y in range(size))
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
output = root / 'ios/IRO/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png'
output.write_bytes(png)
subprocess.run(['sips', '-z', '1024', '1024', str(output)], check=True, capture_output=True)
print('Created opaque 1024 × 1024 AppIcon.png from existing Android vector geometry.')
