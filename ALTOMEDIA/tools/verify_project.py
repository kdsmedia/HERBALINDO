#!/usr/bin/env python3
"""Pemeriksa konsistensi statis proyek HERBALINDO.

Memverifikasi bahwa setiap referensi R.id / R.layout / R.drawable / R.string /
R.color / R.style / R.menu yang dipakai kode Java benar-benar terdefinisi, dan
setiap Activity di manifest memiliki berkas sumbernya. Kesalahan seperti ini
baru terlihat saat runtime (Force Close), sehingga lebih murah ditangkap di sini.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MAIN = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(MAIN, "res")
JAVA = os.path.join(MAIN, "java")
ANDROID = "{http://schemas.android.com/apk/res/android}"

problems = []


def find_all(exts, base):
    out = []
    for dirpath, _dirs, files in os.walk(base):
        for f in files:
            if any(f.endswith(e) for e in exts):
                out.append(os.path.join(dirpath, f))
    return out


layouts = find_all([".xml"], os.path.join(RES, "layout"))
java_files = find_all([".java"], JAVA)

# --- id yang didefinisikan sumber daya (layout @+id dan values id) ---
defined_ids = set()
for path in find_all([".xml"], RES):
    source = open(path, encoding="utf-8").read()
    for m in re.finditer(r'@\+id/([A-Za-z_][A-Za-z0-9_]*)', source):
        defined_ids.add(m.group(1))
    for m in re.finditer(r'<item[^>]*\bname="([A-Za-z_][A-Za-z0-9_]*)"[^>]*\btype="id"', source):
        defined_ids.add(m.group(1))

# --- nama berkas sumber daya lain ---
defined = {"layout": set(), "drawable": set(), "menu": set(), "mipmap": set(), "xml": set(),
           "color": set(), "string": set(), "style": set(), "dimen": set(), "bool": set(),
           "integer": set(), "array": set()}

for kind, dirname in [("layout", "layout"), ("drawable", "drawable"), ("menu", "menu"),
                      ("mipmap", "mipmap-xxxhdpi"), ("xml", "xml")]:
    d = os.path.join(RES, dirname)
    if os.path.isdir(d):
        for f in os.listdir(d):
            defined[kind].add(os.path.splitext(f)[0])

# nilai string/color/style dari seluruh values* (termasuk values-in)
for dirpath, _dirs, files in os.walk(RES):
    if not os.path.basename(dirpath).startswith("values"):
        continue
    for f in files:
        if not f.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(dirpath, f)).getroot()
        except ET.ParseError as e:
            problems.append("%s/%s XML tidak valid: %s" % (os.path.basename(dirpath), f, e))
            continue
        for child in root:
            tag = child.tag
            name = child.get("name")
            if not name:
                continue
            if tag == "string":
                defined["string"].add(name)
            elif tag == "color":
                defined["color"].add(name)
            elif tag == "style":
                defined["style"].add(name)
            elif tag == "dimen":
                defined["dimen"].add(name)
            elif tag == "bool":
                defined["bool"].add(name)
            elif tag == "integer":
                defined["integer"].add(name)
            elif tag == "string-array" or tag == "array":
                defined["array"].add(name)

# --- cek referensi pada kode Java ---
for path in java_files:
    src = open(path, encoding="utf-8").read()
    rel = os.path.relpath(path, ROOT)
    # Lewati referensi framework (android.R.*) — bukan sumber daya aplikasi.
    for m in re.finditer(r'(?<!android\.)\bR\.([a-z]+)\.([A-Za-z_][A-Za-z0-9_]*)', src):
        kind, name = m.group(1), m.group(2)
        if kind == "id":
            if name not in defined_ids:
                problems.append("%s: R.id.%s tidak terdefinisi di layout mana pun" % (rel, name))
        elif kind in defined:
            if name not in defined[kind]:
                problems.append("%s: R.%s.%s tidak ditemukan" % (rel, kind, name))

# --- cek referensi @xx/yy di dalam layout ---
for path in layouts:
    src = open(path, encoding="utf-8").read()
    rel = os.path.relpath(path, ROOT)
    for m in re.finditer(r'"@([a-z]+)/([A-Za-z_][A-Za-z0-9_]*)', src):
        kind, name = m.group(1), m.group(2)
        if kind in ("id",):
            continue
        if kind == "style":
            continue  # gaya dapat diturunkan dari tema platform
        if kind in defined and name not in defined[kind]:
            problems.append("%s: @%s/%s tidak ditemukan" % (rel, kind, name))

# --- Activity di manifest wajib punya kelas ---
manifest = os.path.join(MAIN, "AndroidManifest.xml")
tree = ET.parse(manifest)
for act in tree.getroot().iter("activity"):
    name = act.get(ANDROID + "name")
    if not name or name.startswith(".", 0):
        continue
    fq = name[1:] if name.startswith(".") else name
    pkg_root = os.path.join(JAVA, fq.replace(".", os.sep))
    if not (os.path.exists(pkg_root + ".java") or os.path.exists(pkg_root + ".kt")):
        problems.append("AndroidManifest: %s tidak memiliki berkas sumber" % name)

# --- setiap layout wajib menjadi XML yang valid ---
for path in layouts:
    try:
        ET.parse(path)
    except ET.ParseError as e:
        problems.append("%s XML tidak valid: %s" % (os.path.relpath(path, ROOT), e))

# --- AdView tidak boleh dideklarasikan langsung di layout ---
# SDK iklan menolak AdView XML tanpa atribut "adUnitId" (IllegalArgumentException
# "Required xml attribute \"adUnitId\" was missing."), tetapi adUnitId juga tidak
# dapat ditimpa dari kode ("The ad unit ID can only be set once on AdView."),
# sehingga unit uji tidak mungkin dipakai pada build debug bila banner ditulis di
# XML. Banner wajib dipasang lewat AdsManager.loadBanner(container).
ADS_VIEW = "com.google.android.gms.ads.AdView"
for path in layouts:
    src = open(path, encoding="utf-8").read()
    if ADS_VIEW in src:
        problems.append(
            "%s: AdView tidak boleh ditulis di layout; pasang lewat "
            "AdsManager.loadBanner(container)" % os.path.relpath(path, ROOT))

if problems:
    print("Ditemukan %d masalah:" % len(problems))
    for p in problems:
        print("  -", p)
    sys.exit(1)

print("Pemeriksaan konsistensi LULUS: %d layout, %d berkas Java, %d id."
      % (len(layouts), len(java_files), len(defined_ids)))