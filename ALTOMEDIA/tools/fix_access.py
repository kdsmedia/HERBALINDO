#!/usr/bin/env python3
"""Perbaikan mekanis: hak akses, flag aktif produk, dan tipe kategori."""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ROOT = os.path.abspath(ROOT)


def edit(rel, pairs, required=True):
    path = os.path.join(ROOT, rel)
    s = open(path).read()
    for old, new in pairs:
        if old not in s:
            if required:
                raise SystemExit("tidak ditemukan di %s: %r" % (rel, old[:70]))
            continue
        s = s.replace(old, new, 1)
    open(path, "w").write(s)


edit("app/src/main/java/com/altomedia/herbalindo/data/Repository.java", [
    ('db.put(Config.C_CATEGORIES, "CAT-001", cat("CAT-001", "Herbal Diet"));',
     'db.put(Config.C_CATEGORIES, "CAT-001", cat("CAT-001", "Herbal Diet").toString());'),
    ('db.put(Config.C_CATEGORIES, "CAT-002", cat("CAT-002", "Herbal Keluarga"));',
     'db.put(Config.C_CATEGORIES, "CAT-002", cat("CAT-002", "Herbal Keluarga").toString());'),
])

edit("app/src/main/java/com/altomedia/herbalindo/ui/BaseActivity.java", [
    ("    protected Repository repo;", "    public Repository repo;"),
    ("    protected Models.User user;", "    public Models.User user;"),
    ("    protected Repository repo() { return repo; }", "    public Repository repo() { return repo; }"),
])

edit("app/src/main/java/com/altomedia/herbalindo/data/Models.java", [
    ("        public boolean hasPromo() { return promoPrice > 0 && promoPrice < price; }",
     "        public boolean hasPromo() { return promoPrice > 0 && promoPrice < price; }\n\n"
     "        /** Produk tayang bila status bukan INACTIVE. */\n"
     "        public boolean active() { return !\"INACTIVE\".equals(status); }\n\n"
     "        public void setActive(boolean on) { status = on ? \"ACTIVE\" : \"INACTIVE\"; }"),
])

edit("app/src/main/java/com/altomedia/herbalindo/ui/admin/ProductsSection.java", [
    ('p.name + (p.active ? "" : " (nonaktif)")', 'p.name + (p.active() ? "" : " (nonaktif)")'),
    ('action(p.active ? "Nonaktifkan" : "Aktifkan"', 'action(p.active() ? "Nonaktifkan" : "Aktifkan"'),
    ("edited.active = p.active;", "edited.status = p.status;"),
    ("edited.active = !p.active;", "edited.setActive(!p.active());"),
    ('p.name + (edited.active ? " diaktifkan" : " dinonaktifkan")',
     'p.name + (edited.active() ? " diaktifkan" : " dinonaktifkan")'),
])

print("perbaikan diterapkan")
