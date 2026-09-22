package com.altomedia.herbalindo;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Kelola member oleh admin: pencarian, sunting data, blokir, ubah saldo,
 * dan hapus akun.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.TIRAMISU})
public class AdminMemberManagementTest {
    private Context ctx;
    private Repository repo;
    private Models.User admin;

    @Before public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Repository.with(new MemoryStore());
        Session.clear(ctx);
        repo = Repository.get(ctx);
        admin = repo.user("USR-ADMIN");
    }

    /** Pencarian harus menemukan member lewat ID, nomor HP, dan email. */
    @Test public void pencarianMenemukanLewatIdNomorDanEmail() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        assertEquals(1, repo.searchMembers(u.userId).size());
        assertEquals(1, repo.searchMembers("081234567890").size());
        assertEquals(u.userId, repo.searchMembers("081234567890").get(0).userId);

        repo.adminUpdateMember(u.userId, "Siti Aminah", "siti@contoh.com", "081234567890",
                null, admin.userId);
        List<Models.User> byEmail = repo.searchMembers("siti@contoh.com");
        assertEquals(1, byEmail.size());
        assertEquals(u.userId, byEmail.get(0).userId);

        assertEquals(1, repo.searchMembers(u.referralId).size());
        assertEquals(1, repo.searchMembers("siti").size());
        assertEquals("Tidak ada yang cocok", 0, repo.searchMembers("tidakada").size());
        // Pencarian tidak membocorkan akun admin.
        assertEquals(0, repo.searchMembers("USR-ADMIN").size());
    }

    /** Pencarian kosong mengembalikan seluruh member. */
    @Test public void pencarianKosongMengembalikanSemua() throws Exception {
        repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        repo.register("Budi Santoso", "081234567891", "rahasia1", null);
        assertEquals(2, repo.searchMembers("").size());
        assertEquals(2, repo.searchMembers("   ").size());
        assertEquals(2, repo.searchMembers(null).size());
    }

    /** Admin dapat mengubah nama, email, nomor HP, dan kata sandi member. */
    @Test public void adminDapatMenyuntingDataMember() throws Exception {
        Models.User u = repo.register("Nama Lama", "081234567890", "rahasia1", null);

        repo.adminUpdateMember(u.userId, "Nama Baru", "baru@contoh.com", "089876543210",
                "sandibaru", admin.userId);

        Models.User after = repo.user(u.userId);
        assertEquals("Nama Baru", after.name);
        assertEquals("baru@contoh.com", after.email);
        assertEquals("089876543210", after.phone);
        // Sandi baru benar-benar berlaku, bukan sekadar tersimpan.
        assertEquals(u.userId, repo.login("baru@contoh.com", "sandibaru").userId);
        assertEquals(u.userId, repo.login("089876543210", "sandibaru").userId);

        // Tanpa mengisi sandi, sandi lama tetap berlaku.
        repo.adminUpdateMember(u.userId, "Nama Baru", "baru@contoh.com", "089876543210",
                "", admin.userId);
        assertEquals(u.userId, repo.login("089876543210", "sandibaru").userId);
    }

    /** Data tidak sah harus ditolak dan tidak mengubah apa pun. */
    @Test public void suntingDataTidakSahDitolak() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        Models.User lain = repo.register("Budi Santoso", "081234567891", "rahasia1", null);

        assertThrows(Repository.RuleException.class, () -> repo.adminUpdateMember(
                u.userId, "Si", "a@b.com", "081234567890", null, admin.userId));
        assertThrows(Repository.RuleException.class, () -> repo.adminUpdateMember(
                u.userId, "Siti Aminah", "bukan-email", "081234567890", null, admin.userId));
        assertThrows(Repository.RuleException.class, () -> repo.adminUpdateMember(
                u.userId, "Siti Aminah", "a@b.com", "123", null, admin.userId));
        assertThrows(Repository.RuleException.class, () -> repo.adminUpdateMember(
                u.userId, "Siti Aminah", "", "", null, admin.userId));
        assertThrows(Repository.RuleException.class, () -> repo.adminUpdateMember(
                u.userId, "Siti Aminah", "a@b.com", lain.phone, null, admin.userId));
        assertThrows(Repository.RuleException.class, () -> repo.adminUpdateMember(
                u.userId, "Siti Aminah", "a@b.com", "081234567890", "123", admin.userId));

        Models.User after = repo.user(u.userId);
        assertEquals("Siti Aminah", after.name);
        assertEquals("081234567890", after.phone);
    }

    /** Tambah dan kurangi saldo tercatat pada ledger dan mengubah poin. */
    @Test public void adminDapatMenambahDanMengurangiSaldo() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        long awal = u.points;

        repo.adminAdjustBalance(u.userId, 500000, "bonus lomba", admin.userId);
        assertEquals(awal + 500000, repo.user(u.userId).points);

        repo.adminAdjustBalance(u.userId, -200000, "koreksi salah input", admin.userId);
        assertEquals(awal + 300000, repo.user(u.userId).points);

        // Alasan wajib ada karena setiap perubahan harus dapat diaudit.
        assertThrows(Repository.RuleException.class,
                () -> repo.adminAdjustBalance(u.userId, 1000, "", admin.userId));
        assertThrows(Repository.RuleException.class,
                () -> repo.adminAdjustBalance("USR-TIDAKADA", 1000, "alasan cukup", admin.userId));
    }

    /** Blokir akun mencegah login; aktifkan kembali memulihkannya. */
    @Test public void adminDapatMemblokirDanMengaktifkanAkun() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        repo.setUserStatus(u.userId, "SUSPENDED", admin.userId);
        assertEquals("SUSPENDED", repo.user(u.userId).status);
        Repository.RuleException e = assertThrows(Repository.RuleException.class,
                () -> repo.login("081234567890", "rahasia1"));
        assertTrue(e.getMessage().contains("tidak aktif"));

        repo.setUserStatus(u.userId, "ACTIVE", admin.userId);
        assertEquals(u.userId, repo.login("081234567890", "rahasia1").userId);
    }

    /** Hapus akun membersihkan seluruh data miliknya. */
    @Test public void adminDapatMenghapusAkunBesertaDatanya() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);
        Models.User teman = repo.register("Budi Santoso", "081234567891", "rahasia1", u.referralId);
        repo.addPoints(u.userId, 700000, "ADMIN_CREDIT", "saldo uji", null);

        Models.Product p = repo.allProducts().get(0);
        repo.cartAdd(p.productId, 1);
        Models.Order o = repo.createOrder(u, "Siti", "081234567890", "Jl. Uji 1", "Bandung", "", "");
        repo.markStatus(o, "PAID", admin.userId, "uji");
        repo.watchAd(u.userId);
        repo.checkin(u.userId);

        int ordersBefore = repo.allOrders().size();
        assertTrue(ordersBefore > 0);

        repo.adminDeleteMember(u.userId, "permintaan pemilik akun", admin.userId);

        assertNull("Akun masih ada setelah dihapus", repo.user(u.userId));
        assertEquals("Order milik member tidak ikut terhapus", ordersBefore - 1, repo.allOrders().size());
        for (Models.Order left : repo.allOrders()) assertNotEquals(u.userId, left.userId);
        for (Models.Withdrawal w : repo.allWithdrawals()) assertNotEquals(u.userId, w.userId);
        for (Models.Referral r : repo.allReferrals()) {
            assertNotEquals(u.userId, r.inviterId);
            assertNotEquals(u.userId, r.invitedUserId);
        }
        // Akun lain tidak boleh ikut terhapus.
        assertNotNull(repo.user(teman.userId));
        assertThrows(Repository.RuleException.class, () -> repo.login("081234567890", "rahasia1"));
    }

    /** Penghapusan menuntut alasan dan tidak berlaku untuk akun admin. */
    @Test public void penghapusanDijagaAturannya() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        assertThrows(Repository.RuleException.class,
                () -> repo.adminDeleteMember(u.userId, "", admin.userId));
        assertThrows(Repository.RuleException.class,
                () -> repo.adminDeleteMember("USR-TIDAKADA", "alasan cukup", admin.userId));
        assertThrows(Repository.RuleException.class,
                () -> repo.adminDeleteMember(admin.userId, "alasan cukup", admin.userId));
        assertNotNull("Akun terhapus padahal alasan kosong", repo.user(u.userId));
    }

    /** Setiap tindakan kelola member meninggalkan jejak audit. */
    @Test public void tindakanKelolaMemberTercatatDiAuditLog() throws Exception {
        Models.User u = repo.register("Siti Aminah", "081234567890", "rahasia1", null);

        repo.adminUpdateMember(u.userId, "Siti Aminah", "siti@contoh.com", "081234567890",
                null, admin.userId);
        repo.adminAdjustBalance(u.userId, 100000, "bonus", admin.userId);
        repo.setUserStatus(u.userId, "SUSPENDED", admin.userId);

        java.util.Set<String> actions = new java.util.HashSet<>();
        for (org.json.JSONObject o : repo.adminLogs(0)) actions.add(o.optString("action"));
        assertTrue(actions.contains("MEMBER_EDIT"));
        assertTrue(actions.contains("SALDO_ADJUST"));
        assertTrue(actions.contains("MEMBER_STATUS"));
    }
}
