#!/usr/bin/env python3
"""Toolkit penggambar aset HERBALINDO.

Semua gambar dibentuk dari geometri (persegi, lingkaran, poligon, gradien) dan
palet warna resmi aplikasi, sehingga tidak memerlukan gambar stok atau
placeholder. Hasil: berkas PNG RGBA 8-bit.
"""
import os
import struct
import zlib
import math

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

GREEN_DARK = (27, 94, 32)
GREEN = (46, 125, 50)
GREEN_LIGHT = (102, 187, 106)
GREEN_PALE = (232, 245, 233)
ACCENT = (249, 168, 37)
ACCENT_DARK = (193, 121, 0)
WHITE = (255, 255, 255)
BG = (246, 248, 246)
SURFACE = (255, 255, 255)
SURFACE_ALT = (241, 244, 241)
DIVIDER = (224, 229, 224)
TEXT = (26, 36, 26)
TEXT_SEC = (92, 107, 92)
DANGER = (198, 40, 40)
INFO = (21, 101, 192)
WARNING = (239, 108, 0)
PURPLE = (106, 27, 154)


def blank(w, h, color=(0, 0, 0, 0)):
    return [[list(color) for _ in range(w)] for _ in range(h)]


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def blend(px, color, a):
    if a <= 0:
        return
    if a > 1:
        a = 1.0
    for i in range(3):
        px[i] = clamp(px[i] * (1 - a) + color[i] * a)
    px[3] = clamp(px[3] + (255 - px[3]) * a)


def each_px(img, x0, y0, x1, y1):
    h, w = len(img), len(img[0])
    for y in range(max(0, int(y0)), min(h, int(y1) + 1)):
        for x in range(max(0, int(x0)), min(w, int(x1) + 1)):
            yield x, y, img[y][x]


def fill_rect(img, x0, y0, x1, y1, color, a=1.0):
    for _, _, px in each_px(img, x0, y0, x1, y1):
        blend(px, color, a)


def fill_round_rect(img, x0, y0, x1, y1, r, color, a=1.0):
    for x, y, px in each_px(img, x0, y0, x1, y1):
        dx = max(x0 + r - x, 0, x - (x1 - r))
        dy = max(y0 + r - y, 0, y - (y1 - r))
        if dx * dx + dy * dy <= r * r:
            blend(px, color, a)


def stroke_round_rect(img, x0, y0, x1, y1, r, color, a=1.0, t=1):
    for x, y, px in each_px(img, x0 - t, y0 - t, x1 + t, y1 + t):
        dx = max(x0 + r - x, 0, x - (x1 - r))
        dy = max(y0 + r - y, 0, y - (y1 - r))
        outer = dx * dx + dy * dy <= r * r
        ix0, iy0, ix1, iy1, ir = x0 + t, y0 + t, x1 - t, y1 - t, max(0.0, r - t)
        dx2 = max(ix0 + ir - x, 0, x - (ix1 - ir))
        dy2 = max(iy0 + ir - y, 0, y - (iy1 - ir))
        inner = (ix0 <= x <= ix1 and iy0 <= y <= iy1) and (dx2 * dx2 + dy2 * dy2 <= ir * ir)
        if outer and not inner:
            blend(px, color, a)


def fill_circle(img, cx, cy, r, color, a=1.0):
    for x, y, px in each_px(img, cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1):
        if math.hypot(x - cx, y - cy) <= r:
            blend(px, color, a)


def stroke_circle(img, cx, cy, r, color, a=1.0, t=1.0):
    for x, y, px in each_px(img, cx - r - t - 1, cy - r - t - 1, cx + r + t + 1, cy + r + t + 1):
        d = math.hypot(x - cx, y - cy)
        if r - t <= d <= r:
            blend(px, color, a)


def fill_polygon(img, pts, color, a=1.0):
    if not pts:
        return
    ys = [p[1] for p in pts]
    n = len(pts)
    for y in range(int(min(ys)), int(max(ys)) + 1):
        xs = []
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if (y1 <= y < y2) or (y2 <= y < y1):
                t = (y - y1) / (y2 - y1)
                xs.append(x1 + t * (x2 - x1))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            fill_rect(img, xs[i], y, xs[i + 1], y, color, a)


def linear_gradient(img, c1, c2, diagonal=True):
    h, w = len(img), len(img[0])
    denom = (w + h - 2) if diagonal else max(1, h - 1)
    for y in range(h):
        for x in range(w):
            t = ((x + y) / denom) if diagonal else (y / denom)
            img[y][x] = [clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)] + [255]


def radial_glow(img, cx, cy, r, color, a=0.35):
    for x, y, px in each_px(img, cx - r, cy - r, cx + r, cy + r):
        d = math.hypot(x - cx, y - cy)
        if d <= r:
            blend(px, color, a * (1 - d / r) ** 2)


FONT = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'B': ["11110", "10001", "11110", "10001", "10001", "10001", "11110"],
    'C': ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    'D': ["11100", "10010", "10001", "10001", "10001", "10010", "11100"],
    'E': ["11111", "10000", "11110", "10000", "10000", "10000", "11111"],
    'F': ["11111", "10000", "11110", "10000", "10000", "10000", "10000"],
    'G': ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    'H': ["10001", "10001", "11111", "10001", "10001", "10001", "10001"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'J': ["00111", "00010", "00010", "00010", "10010", "10010", "01100"],
    'K': ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'M': ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'P': ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    'Q': ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    'V': ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    'W': ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    'X': ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    'Y': ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    'Z': ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    '0': ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    '1': ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    '2': ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    '3': ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    '4': ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    '5': ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    '6': ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    '7': ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    '8': ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    '9': ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
    ' ': ["00000", "00000", "00000", "00000", "00000", "00000", "00000"],
    '.': ["00000", "00000", "00000", "00000", "00000", "01100", "01100"],
    ',': ["00000", "00000", "00000", "00000", "01100", "00100", "01000"],
    '-': ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    ':': ["00000", "01100", "01100", "00000", "01100", "01100", "00000"],
    '+': ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
    '/': ["00001", "00010", "00010", "00100", "01000", "01000", "10000"],
    '%': ["11001", "11010", "00010", "00100", "01000", "01011", "10011"],
    '!': ["00100", "00100", "00100", "00100", "00100", "00000", "00100"],
    '(': ["00010", "00100", "01000", "01000", "01000", "00100", "00010"],
    ')': ["01000", "00100", "00010", "00010", "00010", "00100", "01000"],
    '&': ["01100", "10010", "10100", "01000", "10101", "10010", "01101"],
    '=': ["00000", "00000", "11111", "00000", "11111", "00000", "00000"],
    '?': ["01110", "10001", "00001", "00110", "00100", "00000", "00100"],
    '*': ["10101", "01110", "11111", "01110", "10101", "00000", "00000"],
}


def text_width(s, scale, spacing=1):
    return max(0, sum((5 + spacing) * scale for _ in s) - spacing * scale)


def draw_text(img, x, y, s, scale, color, a=1.0, spacing=1):
    cx = x
    for ch in s.upper():
        glyph = FONT.get(ch)
        if glyph is None:
            cx += (5 + spacing) * scale
            continue
        for ry, row in enumerate(glyph):
            for rx, bit in enumerate(row):
                if bit == '1':
                    fill_rect(img, cx + rx * scale, y + ry * scale,
                              cx + rx * scale + scale - 1, y + ry * scale + scale - 1, color, a)
        cx += (5 + spacing) * scale


def draw_text_center(img, y, s, scale, color, a=1.0):
    draw_text(img, (len(img[0]) - text_width(s, scale)) // 2, y, s, scale, color, a)


def draw_text_right(img, x_right, y, s, scale, color, a=1.0):
    draw_text(img, x_right - text_width(s, scale), y, s, scale, color, a)


def draw_leaf(img, cx, cy, size, color=WHITE, a=1.0, ang=-0.6):
    ca, sa = math.cos(ang), math.sin(ang)
    for x, y, px in each_px(img, cx - size, cy - size, cx + size, cy + size):
        dx, dy = x - cx, y - cy
        u = dx * ca + dy * sa
        v = -dx * sa + dy * ca
        if (u / (size * 0.52)) ** 2 + (v / (size * 0.95)) ** 2 <= 1.0:
            blend(px, color, a)


def draw_logo(img, cx, cy, size, leaf_left=WHITE, leaf_right=ACCENT, drop=ACCENT, stem=WHITE):
    draw_leaf(img, cx - size * 0.30, cy - size * 0.16, size * 0.42, leaf_left, 1.0, -0.72)
    draw_leaf(img, cx + size * 0.30, cy - size * 0.16, size * 0.42, leaf_right, 1.0, 0.72)
    fill_round_rect(img, cx - size * 0.035, cy - size * 0.02,
                    cx + size * 0.035, cy + size * 0.60, size * 0.035, stem)
    fill_polygon(img, [(cx, cy + size * 0.28), (cx + size * 0.17, cy + size * 0.60),
                       (cx, cy + size * 0.90), (cx - size * 0.17, cy + size * 0.60)], drop)
    fill_circle(img, cx, cy + size * 0.62, size * 0.115, drop)
    fill_circle(img, cx - size * 0.05, cy + size * 0.58, size * 0.032, WHITE, 0.5)


def draw_icon(img, x, y, size, glyph, color, a=1.0):
    if glyph == 'cart':
        fill_round_rect(img, x + size * .10, y + size * .34, x + size * .90, y + size * .82, size * .14, color, a)
        fill_circle(img, x + size * .30, y + size * .92, size * .085, color, a)
        fill_circle(img, x + size * .72, y + size * .92, size * .085, color, a)
        fill_rect(img, x + size * .16, y + size * .16, x + size * .60, y + size * .21, color, a)
    elif glyph == 'user':
        fill_circle(img, x + size * .5, y + size * .30, size * .19, color, a)
        fill_polygon(img, [(x + size * .12, y + size * .94), (x + size * .88, y + size * .94),
                           (x + size * .74, y + size * .55), (x + size * .26, y + size * .55)], color, a)
    elif glyph == 'coin':
        fill_circle(img, x + size * .5, y + size * .5, size * .44, color, a)
        fill_circle(img, x + size * .5, y + size * .5, size * .30, WHITE, a * .5)
    elif glyph == 'target':
        fill_circle(img, x + size * .5, y + size * .5, size * .45, color, a)
        fill_circle(img, x + size * .5, y + size * .5, size * .28, WHITE, a * .85)
        fill_circle(img, x + size * .5, y + size * .5, size * .11, color, a)
    elif glyph == 'check':
        fill_polygon(img, [(x + size * .10, y + size * .55), (x + size * .40, y + size * .85),
                           (x + size * .95, y + size * .14)], color, a)
    elif glyph == 'leaf':
        draw_leaf(img, x + size * .5, y + size * .5, size * .5, color, a, -0.7)
    elif glyph == 'chart':
        fill_rect(img, x + size * .10, y + size * .55, x + size * .28, y + size * .90, color, a)
        fill_rect(img, x + size * .40, y + size * .35, x + size * .58, y + size * .90, color, a)
        fill_rect(img, x + size * .70, y + size * .15, x + size * .88, y + size * .90, color, a)
    elif glyph == 'box':
        fill_round_rect(img, x + size * .10, y + size * .22, x + size * .90, y + size * .90, size * .10, color, a)
        fill_rect(img, x + size * .10, y + size * .46, x + size * .90, y + size * .52, WHITE, a * .8)
    elif glyph == 'shield':
        fill_polygon(img, [(x + size * .5, y + size * .06), (x + size * .92, y + size * .24),
                           (x + size * .86, y + size * .70), (x + size * .5, y + size * .96),
                           (x + size * .14, y + size * .70), (x + size * .08, y + size * .24)], color, a)
    elif glyph == 'star':
        pts = []
        for i in range(10):
            ang = -math.pi / 2 + i * math.pi / 5
            r = size * (.46 if i % 2 == 0 else .20)
            pts.append((x + size * .5 + r * math.cos(ang), y + size * .5 + r * math.sin(ang)))
        fill_polygon(img, pts, color, a)
    elif glyph == 'wallet':
        fill_round_rect(img, x + size * .06, y + size * .24, x + size * .94, y + size * .82, size * .12, color, a)
        fill_circle(img, x + size * .74, y + size * .53, size * .10, WHITE, a * .9)
    elif glyph == 'settings':
        fill_circle(img, x + size * .5, y + size * .5, size * .30, color, a)
        fill_circle(img, x + size * .5, y + size * .5, size * .14, WHITE, a * .9)
        for i in range(8):
            ang = i * math.pi / 4
            fill_circle(img, x + size * .5 + size * .40 * math.cos(ang),
                        y + size * .5 + size * .40 * math.sin(ang), size * .075, color, a)
    elif glyph == 'log':
        fill_round_rect(img, x + size * .12, y + size * .10, x + size * .88, y + size * .90, size * .10, color, a)
        for i in range(3):
            fill_rect(img, x + size * .24, y + size * (.28 + i * .20),
                      x + size * (.78 - i * .12), y + size * (.32 + i * .20), WHITE, a * .85)


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = bytearray()
    for row in img:
        raw.append(0)
        for px in row:
            raw.extend((px[0], px[1], px[2], px[3]))

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data +
                struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    return path


def upscale(img, n):
    h, w = len(img), len(img[0])
    out = [[None] * (w * n) for _ in range(h * n)]
    for y in range(h):
        for x in range(w):
            px = list(img[y][x])
            for j in range(n):
                row = out[y * n + j]
                for i in range(n):
                    row[x * n + i] = list(px)
    return out