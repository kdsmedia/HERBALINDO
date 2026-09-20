#!/usr/bin/env python3
"""Derives the plain-text legal documents from the HTML pages.

The HTML at the repository root is the single source of truth (GitHub Pages serves
it, and Config.kt links to it). Rendering the .txt copies from that same source
keeps the Play Console text and the published policy from drifting apart.
"""
import html
import os
import re
import textwrap

# tools/ lives inside ALTOMEDIA/, which is itself inside the repository root.
# The HTML legal pages sit at the repository root because GitHub Pages serves them.
ALTOMEDIA = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(ALTOMEDIA)
OUT_DIR = os.path.join(ALTOMEDIA, "playstore", "legal")

DOCS = [
    ("privacy-policy.html", "PRIVACY_POLICY_ID.txt", "KEBIJAKAN PRIVASI"),
    ("terms-of-service.html", "TERMS_OF_SERVICE_ID.txt", "SYARAT DAN KETENTUAN"),
]

WIDTH = 80


def strip_tags(fragment):
    # Tables and lists carry meaning in plain text, so convert structure first.
    s = re.sub(r"</table>", "\n", fragment)

    # Ordered lists keep their decimal numbering; unordered lists become dashes.
    def number_ol(match):
        items = re.findall(r"<li[^>]*>(.*?)</li>", match.group(1), flags=re.S)
        lines = []
        for i, item in enumerate(items, start=1):
            lines.append(f"\n    {i}. {re.sub(r'<[^>]+>', '', item).strip()}")
        return "\n".join(lines) + "\n"

    s = re.sub(r"<ol[^>]*>(.*?)</ol>", number_ol, s, flags=re.S)
    s = re.sub(r"<li[^>]*>", "\n  - ", s)
    s = re.sub(r"<tr[^>]*>", "\n", s)
    s = re.sub(r"</t[dh]>\s*<t[dh][^>]*>", " | ", s)
    s = re.sub(r"<h([1-6])[^>]*>", "\n\n", s)
    s = re.sub(r"</h[1-6]>", "\n", s)
    s = re.sub(r"<p[^>]*>", "\n\n", s)
    s = re.sub(r"<br\s*/?>", "\n", s)
    s = re.sub(r"<(nav|section)[^>]*>", "\n\n", s)
    s = re.sub(r"</(nav|section)>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s


def wrap(text, width=WIDTH):
    out = []
    for line in text.split("\n"):
        line = line.rstrip()
        if not line:
            out.append("")
            continue
        out.extend(
            textwrap.wrap(
                line,
                width=width,
                subsequent_indent=" " * (len(line) - len(line.lstrip())),
                break_long_words=False,
                break_on_hyphens=False,
            ) or [""]
        )
    return "\n".join(out)


def render(src, dst, title):
    raw = open(os.path.join(REPO, src), encoding="utf-8").read()
    body = raw.split("<body", 1)[1].split(">", 1)[1].rsplit("</body>", 1)[0]
    text = strip_tags(body)
    text = re.sub(r"\n{3,}", "\n\n", text).strip()

    header = (
        "=" * WIDTH + "\n"
        + title.center(WIDTH) + "\n"
        + ("HERBALINDO — " + title).center(WIDTH) + "\n"
        + "Pengembang: ALTOMEDIA".center(WIDTH) + "\n"
        + "Kontak: altomediaindonesia@gmail.com".center(WIDTH) + "\n"
        + "Paket: com.altomedia.herbalindo".center(WIDTH) + "\n"
        + "Terakhir diperbarui: 20 September 2026".center(WIDTH) + "\n"
        + "=" * WIDTH + "\n"
        + "\nBerkas ini dihasilkan dari " + src + " oleh tools/gen_legal_txt.py.\n"
        + "Versi resmi yang berlaku adalah halaman HTML pada GitHub Pages.\n"
        + "Jangan sunting berkas ini secara langsung; sunting berkas HTML-nya.\n\n"
    )
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, dst)
    open(path, "w", encoding="utf-8").write(header + wrap(text) + "\n")
    print("wrote", os.path.relpath(path, ALTOMEDIA), len(header + text), "chars")


if __name__ == "__main__":
    for src, dst, title in DOCS:
        render(src, dst, title)