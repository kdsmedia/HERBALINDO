#!/usr/bin/env python3
"""Pindahkan helper baris dari MemberActivity ke kelas Rows agar dapat dipakai
oleh layar member lain tanpa ketergantungan sirkular."""
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))


def read(rel):
    return open(os.path.join(ROOT, rel)).read()


def write(rel, s):
    open(os.path.join(ROOT, rel), "w").write(s)


# --- MemberActivity: hapus helper, tambah import bersih ---
rel = "app/src/main/java/com/altomedia/herbalindo/ui/member/MemberActivity.java"
s = read(rel)
start = s.index("    /* ------------------ helper tampilan kartu ------------------ */")
end = s.rindex("}")
s = s[:start].rstrip() + "\n}\n"
s = s.replace("import android.view.LayoutInflater;\n", "")
s = s.replace("import android.widget.TextView;\n", "")
write(rel, s)

# --- Pakai Rows.info pada HomeTab ---
rel = "app/src/main/java/com/altomedia/herbalindo/ui/member/HomeTab.java"
s = read(rel)
s = s.replace("MemberActivity.cardRow(a, p.name,", "Rows.card(a, p.name,")
write(rel, s)

# --- ProfileTab ---
rel = "app/src/main/java/com/altomedia/herbalindo/ui/member/ProfileTab.java"
s = read(rel)
s = s.replace("MemberActivity.infoRow(a,", "Rows.info(a,")
write(rel, s)

# --- CheckoutActivity ---
rel = "app/src/main/java/com/altomedia/herbalindo/ui/member/CheckoutActivity.java"
s = read(rel)
s = s.replace("MemberActivity.infoRow(this,", "Rows.info(this,")
write(rel, s)

# --- OrderDetailActivity ---
rel = "app/src/main/java/com/altomedia/herbalindo/ui/member/OrderDetailActivity.java"
s = read(rel)
s = s.replace("MemberActivity.infoRow(this,", "Rows.info(this,")
write(rel, s)

print("helper dipindahkan ke Rows")