#!/usr/bin/env python3
"""Perbaikan putaran kedua: penanganan pengecualian dan pemisahan helper baris."""
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))


def edit(rel, pairs, required=True):
    path = os.path.join(ROOT, rel)
    s = open(path).read()
    for old, new in pairs:
        if old not in s:
            if required:
                raise SystemExit("tidak ditemukan di %s: %r" % (rel, old[:80]))
            continue
        s = s.replace(old, new, 1)
    open(path, "w").write(s)


# 1) todayTask: bungkus pembuatan JSON
edit("app/src/main/java/com/altomedia/herbalindo/data/Repository.java", [
    ("""        t.createdAt = Util.nowIso(); t.updatedAt = t.createdAt;
        db.put(Config.C_DAILY_TASKS, t.taskId, t.toJson().toString());
        return t;""",
     """        t.createdAt = Util.nowIso(); t.updatedAt = t.createdAt;
        try { db.put(Config.C_DAILY_TASKS, t.taskId, t.toJson().toString()); }
        catch (Exception e) { throw new IllegalStateException("Gagal menyimpan tugas harian", e); }
        return t;"""),
    ("""            return o;
        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException("Gagal membuat order: " + e.getMessage());
        }""",
     """            return o;
        } catch (Exception e) {
            throw new RuleException("Gagal membuat order: " + e.getMessage());
        }"""),
    ("""            return w;
        } catch (RuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuleException("Gagal mengajukan withdrawal: " + ex.getMessage());
        }""",
     """            return w;
        } catch (Exception ex) {
            throw new RuleException("Gagal mengajukan withdrawal: " + ex.getMessage());
        }"""),
])

print("repository diperbaiki")