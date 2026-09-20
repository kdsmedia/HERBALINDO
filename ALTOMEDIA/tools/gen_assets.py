#!/usr/bin/env python3
"""Generate HERBALINDO app icons, adaptive icons, and Play Store graphics."""
import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "playstore-graphics")

GREEN_DARK = (21, 87, 52)
GREEN = (34, 139, 74)
GREEN_LIGHT = (76, 175, 110)
BEIGE = (245, 240, 228)
GOLD = (197, 160, 89)
WHITE = (255, 255, 255)

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def font(size):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


def leaf(draw, cx, cy, length, width, angle, color):
    pts = []
    n = 24
    for i in range(n + 1):
        t = i / n
        pts.append((length * t, width * math.sin(math.pi * t) / 2))
    for i in range(n, -1, -1):
        t = i / n
        pts.append((length * t, -width * math.sin(math.pi * t) / 2))
    a = math.radians(angle)
    rot = [(cx + x * math.cos(a) - y * math.sin(a),
            cy + x * math.sin(a) + y * math.cos(a)) for x, y in pts]
    draw.polygon(rot, fill=color)


def make_logo(size):
    img = Image.new("RGB", (size, size), GREEN_DARK)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(size - 1, 1)
        c = tuple(int(GREEN_DARK[i] + (GREEN[i] - GREEN_DARK[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=c)

    glow = Image.new("L", (size, size), 0)
    ImageDraw.Draw(glow).ellipse([size * 0.12, size * 0.05, size * 0.88, size * 0.75], fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.09))
    img = Image.composite(Image.new("RGB", (size, size), GREEN_LIGHT), img, glow)
    d = ImageDraw.Draw(img)

    leaf(d, size * 0.50, size * 0.66, size * 0.30, size * 0.17, -70, (255, 255, 255))
    leaf(d, size * 0.50, size * 0.66, size * 0.26, size * 0.145, -110, (215, 245, 225))
    leaf(d, size * 0.50, size * 0.62, size * 0.22, size * 0.12, -90, GREEN_DARK)

    d.line([(size * 0.50, size * 0.68), (size * 0.50, size * 0.88)],
           fill=(255, 255, 255), width=max(2, int(size * 0.026)))

    d.ellipse([size * 0.20, size * 0.20, size * 0.80, size * 0.80],
              outline=GOLD, width=max(2, int(size * 0.014)))
    return img


def make_foreground(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    s = size * 0.62
    leaf(d, c, c + s * 0.20, s * 0.42, s * 0.24, -70, (255, 255, 255, 255))
    leaf(d, c, c + s * 0.20, s * 0.36, s * 0.20, -110, (215, 245, 225, 255))
    leaf(d, c, c + s * 0.14, s * 0.30, s * 0.17, -90, (17, 71, 43, 255))
    d.line([(c, c + s * 0.24), (c, c + s * 0.50)],
           fill=(255, 255, 255, 255), width=max(2, int(size * 0.022)))
    d.ellipse([c - s * 0.52, c - s * 0.52, c + s * 0.52, c + s * 0.52],
              outline=(197, 160, 89, 255), width=max(2, int(size * 0.018)))
    return img


def make_monochrome(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    s = size * 0.62
    leaf(d, c, c + s * 0.20, s * 0.42, s * 0.24, -70, (255, 255, 255, 255))
    leaf(d, c, c + s * 0.20, s * 0.36, s * 0.20, -110, (255, 255, 255, 255))
    return img


def write(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print("wrote", os.path.relpath(path, ROOT))


def feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), GREEN_DARK)
    d = ImageDraw.Draw(img)
    for x in range(W):
        t = x / (W - 1)
        c = tuple(int(GREEN_DARK[i] + (GREEN[i] - GREEN_DARK[i]) * t) for i in range(3))
        d.line([(x, 0), (x, H)], fill=c)
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse([-100, -150, 520, 480], fill=80)
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    img = Image.composite(Image.new("RGB", (W, H), GREEN_LIGHT), img, glow)
    d = ImageDraw.Draw(img)

    img.paste(make_logo(300), (60, (H - 300) // 2))

    d.text((410, 150), "HERBALINDO", font=font(64), fill=WHITE)
    d.text((412, 232), "Toko Herbal Diet Alami", font=font(30), fill=BEIGE)
    d.text((412, 278), "Poin  -  Saldo Rupiah  -  Withdrawal", font=font(24), fill=GOLD)
    d.text((412, 322), "Referral 1 Tingkat  -  Daily Task", font=font(24), fill=BEIGE)
    return img


SCREENSHOT_DATA = {
    "phone-1-home": ("SALDO SAYA", "Rp125.500", ["Check-in  +10 poin", "Rewarded Ads  20/20", "Referral ID  482731"]),
    "phone-2-produk": ("KATALOG PRODUK", "Herbal Diet A", ["Rp100.000  +500 Poin", "Herbal Diet B", "Rp120.000  +600 Poin"]),
    "phone-3-tugas": ("DAILY TASK", "Hari ini", ["Check-in  Selesai", "Rewarded Ads  20/20", "Referral  1 Verified"]),
    "phone-4-saldo": ("SALDO SAYA", "Rp125.500", ["1.255.000 Poin", "Konversi 10.000 = Rp1.000", "TARIK SALDO"]),
    "phone-5-referral": ("REFERRAL SAYA", "482731", ["Verified  5", "Bonus  25.000 Poin", "SALIN   BAGIKAN"]),
    "phone-6-admin": ("ADMIN DASHBOARD", "Ringkasan", ["Total Member  1.240", "Order Pending  12", "Withdrawal Pending  4"]),
}


def screenshot(name, W, H):
    img = Image.new("RGB", (W, H), BEIGE)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 70], fill=GREEN_DARK)
    d.text((30, 22), "Herbalindo", font=font(30), fill=WHITE)
    head, big, rows = SCREENSHOT_DATA[name]
    d.rectangle([40, 110, W - 40, 420], fill=WHITE, outline=GOLD, width=3)
    d.text((70, 145), head, font=font(30), fill=GREEN)
    d.text((70, 200), big, font=font(72), fill=GREEN_DARK)
    y = 480
    for r in rows:
        d.rectangle([40, y, W - 40, y + 130], fill=WHITE)
        d.text((70, y + 45), r, font=font(34), fill=(60, 60, 60))
        y += 155
    d.rectangle([0, H - 150, W, H], fill=WHITE)
    for i, l in enumerate(["Home", "Produk", "Tugas", "Saldo", "Profil"]):
        d.text((60 + i * (W // 5), H - 95), l, font=font(28), fill=GREEN_DARK)
    return img


def main():
    for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                        ("xxhdpi", 144), ("xxxhdpi", 192)]:
        base = os.path.join(RES, "mipmap-" + density)
        logo = make_logo(px)
        write(os.path.join(base, "ic_launcher.png"), logo)
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        rnd = logo.copy()
        rnd.putalpha(mask)
        write(os.path.join(base, "ic_launcher_round.png"), rnd)

    for density, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                        ("xxhdpi", 324), ("xxxhdpi", 432)]:
        base = os.path.join(RES, "mipmap-" + density)
        write(os.path.join(base, "ic_launcher_foreground.png"), make_foreground(px))
        write(os.path.join(base, "ic_launcher_monochrome.png"), make_monochrome(px))

    write(os.path.join(STORE, "icon-512.png"), make_logo(512))
    write(os.path.join(STORE, "feature-graphic-1024x500.png"), feature_graphic())
    for name in SCREENSHOT_DATA:
        write(os.path.join(STORE, "screenshots", name + ".png"), screenshot(name, 1080, 1920))


if __name__ == "__main__":
    main()