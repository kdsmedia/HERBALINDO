package com.altomedia.herbalindo;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.Db;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.admin.AdminActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/**
 * Menjalankan menu Member pada penyimpanan SQLite sungguhan.
 *
 * Pengujian kelola member yang lain memakai penyimpanan tiruan di memori,
 * sehingga jalur yang benar-benar dipakai di perangkat tidak tersentuh.
 * Pengujian ini membuka panel admin dengan basis data yang sama seperti
 * aplikasi agar kegagalan yang hanya muncul di perangkat ikut tertangkap.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34)
public class AdminMembersRealStoreTest {

    private Context ctx;
    private Repository repo;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        // Repository dan basis data sama-sama instans tunggal. Isinya
        // dikosongkan pada penyimpanan yang sedang dipakai, bukan pada berkas
        // basis data, lalu disiapkan ulang seperti saat aplikasi baru dipasang.
        Db.get(ctx).wipeAll();
        repo = Repository.with(Db.get(ctx));
        Session.clear(ctx);
    }

    /** Membuka menu Member pada basis data baru harus berhasil. */
    @Test public void menuMemberTerbukaPadaBasisDataKosong() {
        Models.User admin = repo.user("USR-ADMIN");
        Session.set(ctx, admin);

        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        a.findViewById(R.id.adm_nav_members).performClick();
        View list = a.findViewById(R.id.sec_list);
        assertNotNull("Daftar member tidak terpasang", list);
        c.pause().stop().destroy();
    }

    /**
     * Member tanpa pengundang tidak boleh membuat menu Member berhenti.
     *
     * Nomor pengundang kosong pada member yang mendaftar sendiri. Nilai kosong
     * itu dipakai untuk mencari akun pengundang, dan pada penyimpanan SQLite
     * pencarian dengan nilai kosong pernah membuat aplikasi berhenti.
     */
    @Test public void memberTanpaPengundangTetapTampil() throws Exception {
        Models.User admin = repo.user("USR-ADMIN");
        Session.set(ctx, admin);
        Models.User tanpaPengundang = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        assertNull("Member ini seharusnya tanpa pengundang", tanpaPengundang.referredBy);

        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        a.findViewById(R.id.adm_nav_members).performClick();

        android.widget.LinearLayout list = a.findViewById(R.id.sec_list);
        assertEquals("Kartu member tidak dirender", 1, list.getChildCount());
        android.widget.TextView body = list.getChildAt(0).findViewById(R.id.ac_body);
        assertNotNull("Keterangan kartu member tidak ada", body);
        assertTrue("Pengundang kosong tidak ditampilkan sebagai tanda pisah: " + body.getText(),
                body.getText().toString().contains("Diundang oleh: —"));
        c.pause().stop().destroy();
    }


    /** Membuka menu Member dengan beberapa member nyata harus berhasil. */
    @Test public void menuMemberTerbukaDenganDataMember() throws Exception {
        
        Models.User admin = repo.user("USR-ADMIN");
        Session.set(ctx, admin);
        Models.User satu = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        repo.register("Budi Santoso", "081234567891", "rahasia1", satu.referralId);

        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        a.findViewById(R.id.adm_nav_members).performClick();
        assertNotNull(a.findViewById(R.id.sec_list));
        c.pause().stop().destroy();
    }

    /** Mengetik pencarian di menu Member tidak boleh membuat aplikasi berhenti. */
    @Test public void pencarianMemberTidakBermasalah() throws Exception {
        
        Session.set(ctx, repo.user("USR-ADMIN"));
        repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        a.findViewById(R.id.adm_nav_members).performClick();

        android.widget.EditText search = a.findViewById(R.id.sec_search);
        assertNotNull("Kolom pencarian tidak ditemukan", search);
        search.setText("0812");
        assertNotNull(a.findViewById(R.id.sec_list));
        search.setText("");
        c.pause().stop().destroy();
    }

    /** Setiap tombol pada kartu member harus dapat ditekan tanpa berhenti. */
    @Test public void tombolKartuMemberDapatDitekan() throws Exception {
        
        Session.set(ctx, repo.user("USR-ADMIN"));
        repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        a.findViewById(R.id.adm_nav_members).performClick();

        android.widget.LinearLayout list = a.findViewById(R.id.sec_list);
        assertTrue("Kartu member tidak tampil", list.getChildCount() > 0);
        View card = list.getChildAt(0);
        android.widget.LinearLayout actions = card.findViewById(R.id.ac_actions);
        assertTrue("Tombol aksi member kosong", actions.getChildCount() > 0);
        for (int i = 0; i < actions.getChildCount(); i++) {
            actions.getChildAt(i).performClick();
        }
        c.pause().stop().destroy();
    }

    /** Kelola member dari ujung ke ujung pada penyimpanan sungguhan. */
    @Test public void kelolaMemberBerjalanPadaPenyimpananSungguhan() throws Exception {
        
        Models.User admin = repo.user("USR-ADMIN");
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        repo.adminUpdateMember(u.userId, "Siti Aminah", "siti@contoh.com", "081234567890",
                "sandibaru", admin.userId);
        assertEquals("Siti Aminah", repo.user(u.userId).name);
        assertEquals(u.userId, repo.login("siti@contoh.com", "sandibaru").userId);

        repo.adminAdjustBalance(u.userId, 250000, "bonus uji", admin.userId);
        assertEquals(250000, repo.user(u.userId).points);

        repo.adminSetPoints(u.userId, 10000, "koreksi uji", admin.userId);
        assertEquals(10000, repo.user(u.userId).points);

        repo.setUserStatus(u.userId, "SUSPENDED", admin.userId);
        assertThrows(Repository.RuleException.class, () -> repo.login("siti@contoh.com", "sandibaru"));
        repo.setUserStatus(u.userId, "ACTIVE", admin.userId);
        assertEquals(u.userId, repo.login("siti@contoh.com", "sandibaru").userId);

        assertEquals(1, repo.searchMembers("siti@contoh.com").size());
        assertEquals(1, repo.searchMembers(u.referralId).size());

        repo.adminDeleteMember(u.userId, "uji hapus", admin.userId);
        assertNull(repo.user(u.userId));
    }

    /* ---------- Dialog Ubah Saldo: validasi dan penyimpanan ---------- */

    /** Mengumpulkan seluruh kotak isian pada tampilan dialog. */
    private static void collectEdits(View v, java.util.List<android.widget.EditText> out) {
        if (v instanceof android.widget.EditText) out.add((android.widget.EditText) v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectEdits(g.getChildAt(i), out);
        }
    }

    /** Mengumpulkan seluruh tombol radio pada tampilan dialog. */
    private static void collectRadios(View v, java.util.List<android.widget.RadioButton> out) {
        if (v instanceof android.widget.RadioButton) out.add((android.widget.RadioButton) v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectRadios(g.getChildAt(i), out);
        }
    }

    /** Membuka tombol "Ubah Saldo" pada kartu member pertama. */
    private AdminActivity bukaUbahSaldo() {
        ActivityController<AdminActivity> c = Robolectric.buildActivity(AdminActivity.class).setup();
        AdminActivity a = c.get();
        a.findViewById(R.id.adm_nav_members).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        android.widget.LinearLayout list = a.findViewById(R.id.sec_list);
        assertTrue("Kartu member tidak tampil", list.getChildCount() > 0);
        android.widget.LinearLayout actions = list.getChildAt(0).findViewById(R.id.ac_actions);
        actions.getChildAt(1).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        return a;
    }

    private static android.app.Dialog dialogTerakhir() {
        android.app.Dialog d = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("Dialog Ubah Saldo tidak muncul", d);
        return d;
    }

    /**
     * Mencari tombol positif lewat id bawaannya.
     *
     * Dialog memakai {@code androidx.appcompat.app.AlertDialog} yang bukan
     * turunan {@code android.app.AlertDialog}, sehingga {@code getButton}
     * tidak dapat dipakai dari tipe dasar ini.
     */
    private static android.widget.Button tombolPositif(android.app.Dialog d) {
        java.util.List<View> all = new java.util.ArrayList<>();
        collectAll(d.getWindow().getDecorView(), all);
        for (View v : all) if (v.getId() == android.R.id.button1) return (android.widget.Button) v;
        fail("Tombol simpan tidak ditemukan");
        return null;
    }

    private static void collectAll(View v, java.util.List<View> out) {
        out.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectAll(g.getChildAt(i), out);
        }
    }

    /**
     * Alasan yang kosong harus menahan simpan tanpa menutup dialog.
     *
     * Tombol positif bawaan AlertDialog menutup dialog lebih dahulu, sehingga
     * tanpa penanganan khusus isian yang ditolak tetap menghilang dan admin
     * menyangka saldo sudah berubah.
     */
    @Test public void dialogUbahSaldoTetapTerbukaSaatAlasanKosong() throws Exception {
        Models.User admin = repo.user("USR-ADMIN");
        Session.set(ctx, admin);
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        repo.adminSetPoints(u.userId, 5000, "saldo awal", admin.userId);

        bukaUbahSaldo();
        android.app.Dialog d = dialogTerakhir();
        java.util.List<android.widget.EditText> edits = new java.util.ArrayList<>();
        collectEdits(d.getWindow().getDecorView(), edits);
        edits.get(0).setText("1000");
        edits.get(1).setText("");
        tombolPositif(d).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue("Dialog harus tetap terbuka", d.isShowing());
        assertEquals("Saldo tidak boleh berubah", 5000, repo.user(u.userId).points);
    }

    /** Isian yang lengkap menyimpan perubahan lalu menutup dialog. */
    @Test public void dialogUbahSaldoMenyimpanDanMenutup() throws Exception {
        Models.User admin = repo.user("USR-ADMIN");
        Session.set(ctx, admin);
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        repo.adminSetPoints(u.userId, 5000, "saldo awal", admin.userId);

        bukaUbahSaldo();
        android.app.Dialog d = dialogTerakhir();
        java.util.List<android.widget.EditText> edits = new java.util.ArrayList<>();
        collectEdits(d.getWindow().getDecorView(), edits);
        edits.get(0).setText("1000");
        edits.get(1).setText("bonus kampanye");
        tombolPositif(d).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertEquals("Saldo harus bertambah", 6000, repo.user(u.userId).points);
        assertFalse("Dialog harus tertutup setelah tersimpan", d.isShowing());
    }

    /**
     * Pilihan "Kurangi" memotong saldo, dan pengurangan yang melebihi saldo
     * ditolak sambil membiarkan dialog terbuka agar jumlahnya dapat diperbaiki.
     */
    @Test public void dialogKurangiSaldoMenolakJumlahMelebihiSaldo() throws Exception {
        Models.User admin = repo.user("USR-ADMIN");
        Session.set(ctx, admin);
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        repo.adminSetPoints(u.userId, 5000, "saldo awal", admin.userId);

        bukaUbahSaldo();
        android.app.Dialog d = dialogTerakhir();
        java.util.List<android.widget.EditText> edits = new java.util.ArrayList<>();
        java.util.List<android.widget.RadioButton> radios = new java.util.ArrayList<>();
        collectEdits(d.getWindow().getDecorView(), edits);
        collectRadios(d.getWindow().getDecorView(), radios);
        assertEquals("Pilihan arah harus tersedia", 2, radios.size());
        for (android.widget.RadioButton rb : radios)
            if ("Kurangi".contentEquals(rb.getText())) rb.setChecked(true);
        edits.get(0).setText("99999");
        edits.get(1).setText("koreksi kurang");
        tombolPositif(d).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue("Dialog harus tetap terbuka", d.isShowing());
        assertEquals("Saldo tidak boleh berubah", 5000, repo.user(u.userId).points);
    }
}
