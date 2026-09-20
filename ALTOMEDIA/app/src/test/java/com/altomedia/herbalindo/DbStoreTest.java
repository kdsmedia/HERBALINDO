package com.altomedia.herbalindo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.Db;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Pengujian dengan penyimpanan SQLite sungguhan.
 *
 * Pengujian aturan bisnis memakai penyimpanan tiruan di memori, sehingga jalur
 * yang benar-benar berjalan di perangkat — pembuatan tabel, penyimpanan JSON,
 * dan pemuatan ulang data awal — tidak pernah tersentuh. Pengujian ini memakai
 * BasisData yang sama seperti aplikasi.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34)
public class DbStoreTest {

    private Context ctx;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        ctx.deleteDatabase(Config.DB_NAME);
    }

    /** Aplikasi menyiapkan aturan bisnis dan data awal sebelum layar pertama tampil. */
    @Test public void kelasApplicationMenyiapkanDataSaatAplikasiDibuat() {
        assertTrue("Application yang berjalan bukan HerbalindoApp",
                ctx instanceof HerbalindoApp);
        assertNotNull(HerbalindoApp.get());
        assertNotNull("Repository belum siap saat Application dibuat",
                Repository.get(ctx).user("USR-ADMIN"));
    }

    /** Tabel dibuat dan data awal tersedia pada basis data yang benar-benar baru. */
    @Test public void dataAwalTersediaPadaBasisDataBaru() throws Exception {
        Repository repo = Repository.get(ctx);
        assertNotNull("Akun admin awal tidak dibuat", repo.user("USR-ADMIN"));
        assertEquals("Akun admin awal tidak menyandang peran ADMIN",
                "ADMIN", repo.user("USR-ADMIN").role);
        assertTrue("Katalog produk awal kosong", repo.allProducts().size() > 0);
        assertNotNull("Pengaturan toko tidak dimuat", repo.settings());
    }

    /** Data yang ditulis tetap ada saat dibaca kembali oleh basis data lain pada berkas yang sama. */
    @Test public void dataTersimpanTetapAdaSetelahDibukaUlang() throws Exception {
        Repository repo = Repository.get(ctx);
        Models.User u = repo.register("Budi Santoso", "081234567890", "rahasia1", null);

        Db lagi = Db.get(ctx);
        String json = lagi.get(Config.C_USERS, u.userId);
        assertNotNull("Data pengguna tidak tertulis ke basis data", json);
        assertTrue("Isi dokumen tidak memuat nomor pengguna", json.contains(u.userId));
        // Dua dokumen: akun admin bawaan dan member yang baru mendaftar.
        assertEquals(2, lagi.count(Config.C_USERS));
    }

    /** Penghapusan data benar-benar menghapus dokumennya. */
    @Test public void penghapusanDataMenghilangkanDokumen() throws Exception {
        Repository repo = Repository.get(ctx);
        Models.User u = repo.register("Siti Aminah", "081234567891", "rahasia1", null);
        Db lagi = Db.get(ctx);
        assertNotNull(lagi.get(Config.C_USERS, u.userId));

        lagi.delete(Config.C_USERS, u.userId);
        assertNull("Dokumen masih ada setelah dihapus", lagi.get(Config.C_USERS, u.userId));
    }

    /** Masuk dan keluar memakai sesi tersimpan di berkas preferensi perangkat. */
    @Test public void sesiTersimpanDanTerhapusDenganBenar() throws Exception {
        Repository repo = Repository.get(ctx);
        Models.User u = repo.register("Budi Santoso", "081234567890", "rahasia1", null);

        Session.set(ctx, u);
        Models.User dibaca = Session.current(ctx);
        assertNotNull("Sesi tidak terbaca kembali", dibaca);
        assertEquals(u.userId, dibaca.userId);

        Session.clear(ctx);
        assertNull("Sesi masih ada setelah keluar", Session.current(ctx));
    }

    /** Basis data lama ditingkatkan tanpa kehilangan kemampuan membaca data. */
    @Test public void peningkatanVersiBasisDataTetapDapatDipakai() throws Exception {
        Db db = Db.get(ctx);
        db.put(Config.C_USERS, "USR-UJI", "{\"userId\":\"USR-UJI\"}");
        assertEquals("{\"userId\":\"USR-UJI\"}", db.get(Config.C_USERS, "USR-UJI"));

        db.onUpgrade(db.getWritableDatabase(), 1, Config.DB_VERSION);
        assertNull("Tabel belum dibuat ulang saat peningkatan versi", db.get(Config.C_USERS, "USR-UJI"));
        db.put(Config.C_USERS, "USR-UJI-2", "{\"userId\":\"USR-UJI-2\"}");
        assertEquals("{\"userId\":\"USR-UJI-2\"}", db.get(Config.C_USERS, "USR-UJI-2"));
    }
}