#!/usr/bin/env python3
"""Membangun seluruh aset grafis HERBALINDO.

Keluaran:
  * Ikon peluncur (legacy + adaptive) untuk semua kepadatan
  * Ikon Play Store 512x512
  * Feature graphic 1024x500
  * Tangkapan layar ponsel 1080x1920, tablet 7" 1200x1920, tablet 10" 1600x2560
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from assetkit import *  # noqa

OUT = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store-assets")

DENSITIES = {
    "mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4,
}


def scaled_to(size, draw_fn, factor=1):
    """Gambar pada kanvas logis `size`, lalu perbesar sesuai faktor."""
    img = blank(size[0], size[1])
    draw_fn(img)
    return upscale(img, factor) if factor > 1 else img


# ----------------------------------------------------------------------------
# Ikon aplikasi
# ----------------------------------------------------------------------------
def draw_icon_art(img, rounded=False, padding=0.0):
    w, h = len(img[0]), len(img)
    s = min(w, h)
    if rounded:
        for y in range(h):
            for x in range(w):
                if math.hypot(x - w / 2, y - h / 2) > s / 2:
                    img[y][x][3] = 0
    radial_glow(img, w * 0.72, h * 0.24, s * 0.85, GREEN_LIGHT, 0.30)
    radial_glow(img, w * 0.20, h * 0.86, s * 0.75, ACCENT_DARK, 0.22)
    draw_logo(img, w / 2, h / 2 - s * 0.02 + s * padding, s * 0.40)


def icon_image(px, rounded=False):
    base = blank(px, px)
    linear_gradient(base, GREEN_DARK, GREEN)
    draw_icon_art(base, rounded=rounded)
    return base


def adaptive_foreground(px):
    """Lapisan depan ikon adaptive; lambang hanya menempati area aman (66/108)."""
    img = blank(px, px)
    draw_logo(img, px / 2, px / 2, px * 0.26, WHITE, ACCENT, ACCENT, WHITE)
    return img


def adaptive_background(px):
    img = blank(px, px)
    linear_gradient(img, GREEN_DARK, GREEN)
    radial_glow(img, px * 0.75, px * 0.22, px * 0.7, GREEN_LIGHT, 0.32)
    radial_glow(img, px * 0.22, px * 0.85, px * 0.6, ACCENT_DARK, 0.22)
    return img


def build_icons():
    count = 0
    for name, mult in DENSITIES.items():
        px = int(round(48 * mult))
        d = os.path.join(OUT, "mipmap-" + name)
        write_png(os.path.join(d, "ic_launcher.png"), icon_image(px))
        write_png(os.path.join(d, "ic_launcher_round.png"), icon_image(px, rounded=True))
        fg = int(round(108 * mult))
        write_png(os.path.join(d, "ic_launcher_foreground.png"), adaptive_foreground(fg))
        write_png(os.path.join(d, "ic_launcher_background.png"), adaptive_background(fg))
        count += 4
    # Ikon Play Store
    write_png(os.path.join(STORE, "play_icon_512.png"), icon_image(512))
    count += 1
    print("ikon dibuat:", count)


# ----------------------------------------------------------------------------
# Feature graphic 1024x500
# ----------------------------------------------------------------------------
def build_feature_graphic():
    w, h = 1024, 500
    img = blank(w, h)
    linear_gradient(img, GREEN_DARK, GREEN)
    radial_glow(img, w * 0.12, h * 0.2, 420, GREEN_LIGHT, 0.35)
    radial_glow(img, w * 0.9, h * 0.9, 380, ACCENT_DARK, 0.30)
    for i in range(7):
        rr = 60 + i * 34
        stroke_circle(img, w * 0.80, h * 0.52, rr, WHITE, 0.05, 2)

    draw_logo(img, 150, h / 2, 130)
    draw_text(img, 260, 168, "HERBALINDO", 11, WHITE)
    draw_text(img, 262, 268, "HERBAL DIET ALAMI", 4, ACCENT)
    draw_text(img, 262, 320, "POIN  REFERRAL  SALDO", 3, GREEN_PALE)
    fill_round_rect(img, 262, 356, 640, 400, 22, ACCENT, 0.95)
    draw_text(img, 286, 368, "CASHBACK RP", 4, GREEN_DARK)
    draw_text(img, 470, 368, "1.000", 4, GREEN_DARK)

    box_w, box_h = 300, 132
    bx, by = w - box_w - 60, 96
    fill_round_rect(img, bx, by, bx + box_w, by + box_h, 16, WHITE, 0.94)
    fill_circle(img, bx + 38, by + 38, 22, BRAND_ACCENT_RING := ACCENT)
    draw_text(img, bx + 68, by + 26, "REFERRAL", 3, TEXT)
    draw_text(img, bx + 68, by + 56, "6 DIGIT", 3, GREEN)
    draw_text(img, bx + 22, by + 92, "1 TINGKAT SAJA", 3, TEXT_SEC)

    fill_round_rect(img, 262, 402, 900, 436, 17, WHITE, 0.12)
    draw_text(img, 282, 412, "CHECK-IN HARIAN +10 POIN  -  IKLAN +5 POIN  -  MAKS 20/HARI", 2, GREEN_PALE)

    write_png(os.path.join(STORE, "feature_graphic_1024x500.png"), img)
    print("feature graphic dibuat")


# ----------------------------------------------------------------------------
# Kerangka tangkapan layar
# ----------------------------------------------------------------------------
class Screen:
    def __init__(self, w, h, scale):
        self.W, self.H, self.scale = w, h, scale
        self.img = blank(w, h)
        self.img_px = upscale(self.img, scale)

    # Koordinat memakai satuan logis lalu dikalikan scale saat menulis
    def _s(self, v):
        return v * self.scale

    def bg(self, color=BG):
        fill_rect(self.img, 0, 0, self.W - 1, self.H - 1, color)

    def status_bar(self, dark=False):
        c = WHITE if dark else GREEN_DARK
        fill_rect(self.img, 0, 0, self.W, 22, c)
        txt = GREEN_PALE if dark else GREEN_PALE
        draw_text(self.img, 10, 7, "09:41", 2, txt)
        for i in range(3):
            fill_rect(self.img, self.W - 26 + i * 6, 8, self.W - 24 + i * 6, 14, txt)
        fill_round_rect(self.img, self.W - 44, 7, self.W - 30, 15, 3, txt)

    def appbar(self, title, subtitle=None, back=False):
        h = 46 if subtitle else 40
        fill_rect(self.img, 0, 22, self.W, 22 + h, GREEN_DARK)
        x = 14
        if back:
            fill_polygon(self.img, [(x + 8, 33), (x, 40), (x + 8, 47)], WHITE)
            x += 20
        draw_text(self.img, x, 33, title, 3, WHITE)
        if subtitle:
            draw_text(self.img, x, 50, subtitle, 2, GREEN_PALE)
        return 22 + h

    def snackbar(self, text, y):
        fill_round_rect(self.img, 12, y, self.W - 12, y + 26, 8, TEXT, 0.92)
        draw_text(self.img, 24, y + 9, text, 2, WHITE)

    def bottom_nav(self, active=0):
        y0 = self.H - 52
        fill_rect(self.img, 0, y0, self.W, self.H, SURFACE)
        fill_rect(self.img, 0, y0, self.W, y0 + 1, DIVIDER)
        labels = [("leaf", "HOME"), ("box", "PRODUK"), ("target", "TUGAS"),
                  ("wallet", "SALDO"), ("user", "PROFIL")]
        w = self.W / 5
        for i, (glyph, label) in enumerate(labels):
            cx = w * i + w / 2
            col = GREEN_DARK if i == active else TEXT_SEC
            draw_icon(self.img, cx - 9, y0 + 8, 18, glyph, col)
            draw_text(self.img, int(cx - text_width(label, 1) / 2), y0 + 32, label, 1, col)
        return y0

    def card(self, x0, y0, x1, y1, radius=10):
        fill_round_rect(self.img, x0 + 1, y0 + 1, x1 + 1, y1 + 1, radius, (200, 205, 200), 0.35)
        fill_round_rect(self.img, x0, y0, x1, y1, radius, SURFACE)

    def button(self, x0, y0, x1, y1, text, color=GREEN, text_color=WHITE, radius=10):
        fill_round_rect(self.img, x0, y0, x1, y1, radius, color)
        draw_text(self.img, int((x0 + x1) / 2 - text_width(text, 2) / 2),
                  int((y0 + y1) / 2 - 6), text, 2, text_color)

    def chip(self, x0, y0, text, color=GREEN_PALE, text_color=GREEN_DARK):
        w = text_width(text, 1) + 14
        fill_round_rect(self.img, x0, y0, x0 + w, y0 + 14, 7, color)
        draw_text(self.img, x0 + 7, y0 + 4, text, 1, text_color)
        return x0 + w + 5

    def save(self, path):
        write_png(path, upscale(self.img, self.scale))


# ------------------------- layar-layar -------------------------
def screen_splash(s):
    fill_rect(s.img, 0, 0, s.W, s.H, GREEN_DARK)
    radial_glow(s.img, s.W * 0.8, s.H * 0.15, 260, GREEN_LIGHT, 0.35)
    radial_glow(s.img, s.W * 0.15, s.H * 0.9, 240, ACCENT_DARK, 0.3)
    draw_logo(s.img, s.W / 2, s.H * 0.40, 78)
    draw_text_center(s.img, int(s.H * 0.52), "HERBALINDO", 6, WHITE)
    draw_text_center(s.img, int(s.H * 0.57), "HERBAL DIET ALAMI", 2, ACCENT)
    draw_text_center(s.img, int(s.H * 0.93), "ALTOMEDIA", 2, GREEN_PALE)


def screen_login(s):
    s.bg()
    s.status_bar()
    h = s.appbar("HERBALINDO")
    draw_logo(s.img, s.W / 2, h + 46, 34)
    draw_text_center(s.img, int(h + 84), "SELAMAT DATANG", 3, TEXT)
    draw_text_center(s.img, int(h + 100), "MASUK UNTUK MELANJUTKAN", 2, TEXT_SEC)

    y = int(h + 122)
    for label in ["EMAIL / NOMOR HP", "PASSWORD"]:
        s.card(12, y, s.W - 12, y + 34)
        draw_text(s.img, 22, y + 7, label, 1, TEXT_SEC)
        draw_text(s.img, 22, y + 18, "SITI@CONTOH.COM" if label.startswith("EMAIL") else "........", 2, TEXT)
        y += 44
    s.button(12, y, s.W - 12, y + 32, "MASUK")
    y += 42
    draw_text_center(s.img, y, "DAFTAR AKUN BARU", 2, GREEN_DARK)
    y += 22
    fill_round_rect(s.img, 12, y, s.W - 12, y + 34, 8, GREEN_PALE)
    draw_text(s.img, 22, y + 6, "SISTEM SATU PINTU", 1, GREEN_DARK)
    draw_text(s.img, 22, y + 18, "MEMBER  &  ADMIN, ROLE DARI SERVER", 1, TEXT_SEC)


def screen_home(s):
    s.bg()
    s.status_bar(dark=False)
    y = s.appbar("HALO, SITI", "REF 834228")
    s.card(12, y + 10, s.W - 12, y + 76, 12)
    fill_round_rect(s.img, 12, y + 10, s.W - 12, y + 34, 12, GREEN)
    draw_text(s.img, 24, y + 17, "SALDO ANDA", 2, GREEN_PALE)
    draw_text_right(s.img, s.W - 24, y + 17, "REF 834228", 1, GREEN_PALE)
    draw_text(s.img, 24, y + 40, "RP1.000", 4, GREEN_DARK)
    draw_text(s.img, 24, y + 60, "100 POIN  =  10.000 POIN / RP1.000", 1, TEXT_SEC)

    draw_text(s.img, 14, y + 88, "MENU CEPAT", 1, TEXT_SEC)
    bx = 12
    for glyph, label in [("target", "CHECK-IN"), ("star", "IKLAN"), ("wallet", "SALDO"), ("user", "REFERRAL")]:
        s.card(bx, y + 98, bx + 74, y + 154, 10)
        draw_icon(s.img, bx + 27, y + 108, 20, glyph, GREEN_DARK)
        draw_text(s.img, bx + 6, y + 136, label, 1, TEXT_SEC)
        bx += 80

    draw_text(s.img, 14, y + 166, "PRODUK PILIHAN", 1, TEXT_SEC)
    px = 12
    for i, (nm, sub) in enumerate([("HERBAL DIET A", "RP85.000"), ("HERBAL DIET B", "RP120.000"), ("HERBAL DIET C", "RP79.000")]):
        s.card(px, y + 178, px + 96, y + 262, 10)
        fill_round_rect(s.img, px + 6, y + 186, px + 90, y + 222, 8, GREEN_PALE)
        draw_logo(s.img, px + 48, y + 204, 20, GREEN, GREEN_DARK, ACCENT, GREEN)
        draw_text(s.img, px + 6, y + 228, nm, 1, TEXT)
        draw_text(s.img, px + 6, y + 242, sub, 2, GREEN_DARK)
        px += 104
    s.bottom_nav(0)


def screen_products(s):
    s.bg()
    s.status_bar()
    y = s.appbar("PRODUK", "3 PRODUK TERSEDIA")
    y += 6
    for i, (nm, price, promo, stok) in enumerate([
            ("HERBAL DIET A", "RP100.000", "RP85.000", "STOK 50"),
            ("HERBAL DIET B", "RP120.000", None, "STOK 40"),
            ("HERBAL DIET C", "RP95.000", "RP79.000", "STOK 35")]):
        s.card(12, y, s.W - 12, y + 78, 12)
        fill_round_rect(s.img, 20, y + 10, 78, y + 68, 8, GREEN_PALE)
        draw_logo(s.img, 49, y + 39, 22, GREEN, GREEN_DARK, ACCENT, GREEN)
        draw_text(s.img, 88, y + 12, nm, 2, TEXT)
        draw_text(s.img, 88, y + 30, "RAMUAN HERBAL ALAMI", 1, TEXT_SEC)
        draw_text(s.img, 88, y + 46, promo or price, 3, GREEN_DARK)
        if promo:
            draw_text(s.img, 88 + text_width(promo, 3) + 8, y + 48, price, 1, TEXT_SEC)
        t = s.chip(88, y + 60, stok)
        s.chip(t, y + 60, "TERSEDIA", GREEN_PALE, GREEN_DARK)
        y += 86
    s.bottom_nav(1)


def screen_product_detail(s):
    s.bg()
    s.status_bar()
    y = s.appbar("DETAIL PRODUK", back=True)
    fill_round_rect(s.img, 12, y + 8, s.W - 12, y + 120, 12, GREEN_PALE)
    draw_logo(s.img, s.W / 2, y + 64, 40, GREEN, GREEN_DARK, ACCENT, GREEN)
    y += 128
    draw_text(s.img, 14, y, "HERBAL DIET A", 3, TEXT)
    draw_text(s.img, 14, y + 22, "HBA-001  -  HERBAL DIET", 1, TEXT_SEC)
    draw_text(s.img, 14, y + 38, "RP85.000", 4, GREEN_DARK)
    draw_text(s.img, 14 + text_width("RP85.000", 4) + 8, y + 42, "RP100.000", 1, TEXT_SEC)
    y += 64
    s.card(12, y, s.W - 12, y + 76, 10)
    draw_text(s.img, 22, y + 8, "KOMPOSISI", 1, GREEN_DARK)
    draw_text(s.img, 22, y + 22, "EKSTRAK KELOR, JAHE MERAH,", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 34, "TEMULAWAK, DAUN SIRS, MADU", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 50, "ATURAN PAKAI", 1, GREEN_DARK)
    draw_text(s.img, 22, y + 64, "1 SACHET / HARI SETELAH MAKAN", 1, TEXT_SEC)
    y += 88
    s.button(12, y, s.W - 12, y + 34, "+ TAMBAH KE KERANJANG")
    s.bottom_nav(1)


def screen_checkout(s):
    s.bg()
    s.status_bar()
    y = s.appbar("CHECKOUT", back=True)
    y += 6
    s.card(12, y, s.W - 12, y + 92, 10)
    draw_text(s.img, 22, y + 8, "ALAMAT PENGIRIMAN", 1, GREEN_DARK)
    draw_text(s.img, 22, y + 22, "SITI AMINAH  -  081234567890", 1, TEXT)
    draw_text(s.img, 22, y + 36, "JL. MELATI NO. 12, RT 02/RW 05", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 48, "KARAWANG BARAT, KARAWANG 41361", 1, TEXT_SEC)
    fill_rect(s.img, 22, y + 64, s.W - 22, y + 65, DIVIDER)
    draw_text(s.img, 22, y + 72, "KURIR: JNE  -  ONGKIR RP15.000", 1, TEXT_SEC)
    y += 104
    s.card(12, y, s.W - 12, y + 74, 10)
    draw_text(s.img, 22, y + 8, "RINGKASAN PESANAN", 1, GREEN_DARK)
    draw_text(s.img, 22, y + 24, "HERBAL DIET A X 1", 1, TEXT)
    draw_text_right(s.img, s.W - 22, y + 24, "RP85.000", 1, TEXT)
    draw_text(s.img, 22, y + 38, "ONGKIR JNE", 1, TEXT)
    draw_text_right(s.img, s.W - 22, y + 38, "RP15.000", 1, TEXT)
    fill_rect(s.img, 22, y + 52, s.W - 22, y + 53, DIVIDER)
    draw_text(s.img, 22, y + 58, "TOTAL", 2, TEXT)
    draw_text_right(s.img, s.W - 22, y + 58, "RP100.000", 2, GREEN_DARK)
    y += 86
    fill_round_rect(s.img, 12, y, s.W - 12, y + 30, 8, GREEN_PALE)
    draw_text(s.img, 22, y + 10, "PEMBAYARAN: QRIS (DINAMIS)", 1, GREEN_DARK)
    y += 40
    s.button(12, y, s.W - 12, y + 34, "BAYAR SEKARANG")
    s.bottom_nav(1)


def screen_payment(s):
    s.bg()
    s.status_bar()
    y = s.appbar("PEMBAYARAN", "ORD-20260920-000124", back=True)
    y += 8
    s.card(12, y, s.W - 12, y + 210, 12)
    draw_text_center(s.img, y + 10, "SCAN QRIS", 3, TEXT)
    draw_text_center(s.img, y + 30, "NOMINAL RP100.000", 2, GREEN_DARK)
    qs, qy = 118, y + 48
    fill_round_rect(s.img, int(s.W / 2 - qs / 2) - 6, qy - 6, int(s.W / 2 + qs / 2) + 6, qy + qs + 6, 8, WHITE)
    n = 21
    cell = qs / n
    # pola QR dekoratif deterministik (bukan data acak)
    for r in range(n):
        for c in range(n):
            in_finder = ((r < 7 and c < 7) or (r < 7 and c >= n - 7) or (r >= n - 7 and c < 7))
            on = in_finder and (r in (0, 6) or c in (0, 6) or (2 <= r <= 4 and 2 <= c <= 4)
                                or (2 <= r <= 4 and n - 5 <= c <= n - 3)
                                or (n - 5 <= r <= n - 3 and 2 <= c <= 4))
            if not in_finder:
                on = ((r * 7 + c * 13 + (r ^ c)) % 5) in (0, 1)
            if on:
                fill_rect(s.img, s.W / 2 - qs / 2 + c * cell, qy + r * cell,
                          s.W / 2 - qs / 2 + (c + 1) * cell - 1, qy + (r + 1) * cell - 1, GREEN_DARK)
    y += 222
    draw_text_center(s.img, y, "ALTOMEDIA, GROSIR  -  KARAWANG", 1, TEXT_SEC)
    y += 16
    draw_text_center(s.img, y, "APPLICATION ID: 0002010102112661...6304D21A", 1, TEXT_SEC)
    y += 20
    s.button(12, y, int(s.W / 2) - 6, y + 32, "UNDUH QR", GREEN_PALE, GREEN_DARK)
    s.button(int(s.W / 2) + 6, y, s.W - 12, y + 32, "SAYA SUDAH BAYAR")


def screen_tasks(s):
    s.bg()
    s.status_bar()
    y = s.appbar("TUGAS HARIAN", "HARI INI")
    y += 8
    s.card(12, y, s.W - 12, y + 62, 10)
    draw_icon(s.img, 24, y + 12, 26, "target", GREEN_DARK)
    draw_text(s.img, 60, y + 14, "CHECK-IN HARIAN", 2, TEXT)
    draw_text(s.img, 60, y + 32, "BONUS +10 POIN / HARI", 1, TEXT_SEC)
    s.button(s.W - 108, y + 16, s.W - 20, y + 48, "AMBIL")
    y += 72
    s.card(12, y, s.W - 12, y + 96, 10)
    draw_icon(s.img, 24, y + 12, 26, "star", ACCENT_DARK)
    draw_text(s.img, 60, y + 14, "TONTON IKLAN", 2, TEXT)
    draw_text(s.img, 60, y + 32, "REWARDED ADS +5 POIN", 1, TEXT_SEC)
    fill_rect(s.img, 60, y + 46, s.W - 24, y + 52, SURFACE_ALT)
    fill_round_rect(s.img, 60, y + 46, 60 + (s.W - 84) * 0.3, y + 52, 3, ACCENT)
    draw_text(s.img, 60, y + 58, "6 / 20 IKLAN HARI INI", 1, TEXT_SEC)
    s.button(s.W - 108, y + 50, s.W - 20, y + 82, "TONTON")
    y += 106
    s.card(12, y, s.W - 12, y + 70, 10)
    draw_icon(s.img, 24, y + 12, 26, "user", GREEN_DARK)
    draw_text(s.img, 60, y + 14, "UNDANG TEMAN", 2, TEXT)
    draw_text(s.img, 60, y + 32, "BONUS 5.000 POIN SAAT ORDER", 1, TEXT_SEC)
    draw_text(s.img, 60, y + 48, "MINIMAL TRANSAKSI RP50.000", 1, TEXT_SEC)
    s.button(s.W - 96, y + 24, s.W - 20, y + 56, "BAGIKAN")
    y += 80
    fill_round_rect(s.img, 12, y, s.W - 12, y + 56, 10, GREEN_PALE)
    draw_text(s.img, 22, y + 8, "SYARAT WITHDRAWAL TRANSPARAN", 1, GREEN_DARK)
    draw_text(s.img, 22, y + 24, "1. SALDO MINIMAL RP50.000", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 36, "2. IKLAN HARI INI 20/20", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 48, "3. MAKSIMAL 1X WITHDRAW / HARI", 1, TEXT_SEC)
    s.bottom_nav(2)


def screen_balance(s):
    s.bg()
    s.status_bar()
    y = s.appbar("SALDO & WITHDRAWAL")
    y += 8
    s.card(12, y, s.W - 12, y + 84, 12)
    fill_round_rect(s.img, 12, y, s.W - 12, y + 28, 12, GREEN)
    draw_text(s.img, 24, y + 8, "SALDO TERSEDIA", 2, GREEN_PALE)
    draw_text(s.img, 24, y + 36, "RP1.000", 5, GREEN_DARK)
    draw_text(s.img, 24, y + 62, "100 POIN  -  KONVERSI 10.000 POIN = RP1.000", 1, TEXT_SEC)
    y += 96
    draw_text(s.img, 14, y, "SYARAT WITHDRAWAL", 1, TEXT_SEC)
    y += 12
    for ok, label in [(True, "AKUN AKTIF"), (False, "SALDO MINIMAL RP100"),
                      (False, "IKLAN HARI INI 6/20"), (True, "MAKSIMAL 1X / HARI")]:
        s.card(12, y, s.W - 12, y + 26, 8)
        if ok:
            draw_icon(s.img, 20, y + 5, 16, "check", GREEN)
        else:
            fill_circle(s.img, 28, y + 13, 7, WARNING)
            draw_text(s.img, 26, y + 9, "!", 2, WHITE)
        draw_text(s.img, 44, y + 9, label, 2, TEXT if ok else TEXT_SEC)
        y += 32
    y += 4
    s.card(12, y, s.W - 12, y + 40, 10)
    draw_text(s.img, 22, y + 8, "NOMINAL PENARIKAN", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 20, "RP20.000", 3, TEXT)
    draw_text(s.img, 22, y + 34, "DIPILIH DARI DAFTAR - TIDAK DIKETIK BEBAS", 1, TEXT_SEC)
    y += 50
    s.card(12, y, s.W - 12, y + 34, 10)
    draw_text(s.img, 22, y + 7, "METODE", 1, TEXT_SEC)
    draw_text(s.img, 22, y + 18, "DANA  -  081234567890", 2, TEXT)
    y += 44
    draw_text_center(s.img, y, "TOMBOL AKTIF SETELAH SEMUA SYARAT TERPENUHI", 1, TEXT_SEC)
    s.bottom_nav(3)


def screen_admin(s):
    s.bg()
    s.status_bar()
    fill_rect(s.img, 0, 22, s.W, 62, (20, 40, 22))
    draw_text(s.img, 14, 32, "PANEL ADMIN", 3, WHITE)
    draw_text(s.img, 14, 50, "ALTOMEDIA  -  OPERASIONAL TOKO", 1, GREEN_PALE)
    y = 70
    stats = [("TOTAL MEMBER", "1"), ("ORDER PENDING", "1"), ("WITHDRAWAL", "0"),
             ("TOTAL POIN", "10"), ("TOTAL SALDO", "RP1"), ("REFERRAL", "0")]
    bx, by = 12, y
    for i, (label, value) in enumerate(stats):
        col = i % 3
        row = i // 3
        x0 = bx + col * ((s.W - 24) / 3 + 2)
        x1 = x0 + (s.W - 24) / 3 - 4
        y0 = by + row * 62
        s.card(x0, y0, x1, y0 + 56, 10)
        draw_text(s.img, x0 + 8, y0 + 8, label, 1, TEXT_SEC)
        draw_text(s.img, x0 + 8, y0 + 22, value, 4, GREEN_DARK)
    y = by + 130
    draw_text(s.img, 14, y, "AKTIVITAS & KENDALI", 1, TEXT_SEC)
    y += 12
    for glyph, label, sub in [("check", "KELOLA PESANAN", "VERIFIKASI BAYAR, RESI, REFUND"),
                              ("wallet", "WITHDRAWAL", "SETUJUI / TOLAK PENGAJUAN"),
                              ("box", "PRODUK & STOK", "HARGA, STOK, KATALOG"),
                              ("user", "MEMBER", "STATUS, FRAUD, SALDO POIN"),
                              ("settings", "PENGATURAN", "POIN, IKLAN, MINIMAL WD"),
                              ("log", "AUDIT LOG", "REKAM JEJAK SEMUA AKSI ADMIN")]:
        s.card(12, y, s.W - 12, y + 38, 10)
        draw_icon(s.img, 20, y + 9, 20, glyph, GREEN_DARK)
        draw_text(s.img, 50, y + 8, label, 2, TEXT)
        draw_text(s.img, 50, y + 24, sub, 1, TEXT_SEC)
        fill_polygon(s.img, [(s.W - 22, y + 15), (s.W - 14, y + 20), (s.W - 22, y + 25)], TEXT_SEC)
        y += 44
    s.save


def build_screenshots():
    plans = [
        ("phone", 360, 640, 3, [screen_splash, screen_login, screen_home, screen_products,
                                screen_product_detail, screen_checkout, screen_payment,
                                screen_tasks, screen_balance, screen_admin]),
        ("tablet7", 400, 640, 3, [screen_home, screen_tasks, screen_admin]),
        ("tablet10", 400, 640, 4, [screen_home, screen_balance]),
    ]
    total = 0
    for folder, w, h, scale, fns in plans:
        out = os.path.join(STORE, "screenshots", folder)
        for i, fn in enumerate(fns, start=1):
            s = Screen(w, h, scale)
            s.save = lambda path, self=s: write_png(path, upscale(self.img, self.scale))
            fn(s)
            name = fn.__name__.replace("screen_", "")
            path = os.path.join(out, "%02d_%s.png" % (i, name))
            s.save(path)
            total += 1
    print("tangkapan layar dibuat:", total)


if __name__ == "__main__":
    build_icons()
    build_feature_graphic()
    build_screenshots()
    print("lokasi aset toko:", STORE)