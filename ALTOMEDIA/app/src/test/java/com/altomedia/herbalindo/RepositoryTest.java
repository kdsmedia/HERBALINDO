package com.altomedia.herbalindo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.QrisGenerator;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.MemoryStore;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

import org.junit.Before;
import org.junit.Test;

/** Pengujian aturan bisnis inti (Bab 2, 3, 4, 5, 6, 7, 8, 9, 13). */
public class RepositoryTest {

    private Repository repo;

    @Before public void setUp() { repo = Repository.with(new MemoryStore()); }

    private Models.User member(String name, String contact, String ref) throws Exception {
        return repo.register(name, contact, "rahasia1", ref);
    }

    private Models.Order buy(Models.User u, String productId, int qty) throws Exception {
        repo.cartAdd(productId, qty);
        return repo.createOrder(u, u.name, u.contact(), "Jl. Melati No. 12", "Karawang", "41361", "");
    }

    /* ---------------- Bab 2: autentikasi & referral ID ---------------- */

    @Test public void referralIdIsSixDigitsAndUnique() throws Exception {
        Models.User a = member("Siti Aminah", "081234567890", null);
        Models.User b = member("Budi Santoso", "081234567891", null);
        assertTrue(a.referralId.matches("^\\d{6}$"));
        assertTrue(b.referralId.matches("^\\d{6}$"));
        assertFalse(a.referralId.equals(b.referralId));
    }

    @Test public void adminCanLoginWithSeededCredentials() throws Exception {
        Models.User admin = repo.login(Repository.ADMIN_EMAIL, Repository.ADMIN_PASSWORD);
        assertEquals("ADMIN", admin.role);
        assertEquals("000001", admin.referralId);
    }

    /** Admin dapat masuk memakai nomor HP, bukan hanya email. */
    @Test public void adminCanLoginWithPhone() throws Exception {
        Models.User admin = repo.login(Repository.ADMIN_PHONE, Repository.ADMIN_PASSWORD);
        assertEquals("ADMIN", admin.role);
    }

    /**
     * Perangkat yang sudah terpasang menyimpan kredensial admin lama, dan
     * {@code seed()} tidak berjalan lagi karena koleksi pengguna tidak kosong.
     * Penyesuaian sekali jalan harus membuat akun lama dapat memakai kredensial
     * baru tanpa menghapus data yang sudah ada.
     */
    @Test public void kredensialAdminLamaDiperbaruiTanpaMenghapusData() throws Exception {
        MemoryStore store = new MemoryStore();
        String salt = "salt-lama";
        Models.User lama = new Models.User();
        lama.userId = "USR-ADMIN";
        lama.name = "Administrator";
        lama.email = "admin@herbalindo.id";
        lama.phone = "081200000000";
        lama.salt = salt;
        lama.passwordHash = com.altomedia.herbalindo.core.PasswordHasher.hash("admin123", salt);
        lama.referralId = "000001";
        lama.role = "ADMIN";
        lama.status = "ACTIVE";
        lama.createdAt = lama.updatedAt = "2026-01-01T00:00:00Z";
        store.put(Config.C_USERS, "USR-ADMIN", lama.toJson().toString());

        Models.User anggota = new Models.User();
        anggota.userId = "USR-1";
        anggota.name = "Siti Aminah";
        anggota.email = "siti@contoh.id";
        anggota.phone = "081234567890";
        anggota.salt = "s";
        anggota.passwordHash = "h";
        anggota.referralId = "123456";
        anggota.role = "MEMBER";
        anggota.status = "ACTIVE";
        anggota.createdAt = anggota.updatedAt = "2026-01-01T00:00:00Z";
        store.put(Config.C_USERS, "USR-1", anggota.toJson().toString());

        Repository sesudah = Repository.with(store);
        assertEquals("USR-ADMIN",
                sesudah.login(Repository.ADMIN_EMAIL, Repository.ADMIN_PASSWORD).userId);
        assertEquals("Nomor HP admin tidak diperbarui",
                Repository.ADMIN_PHONE, sesudah.user("USR-ADMIN").phone);
        assertNotNull("Akun anggota lama ikut terhapus",
                sesudah.user("USR-1"));
    }

    @Test public void wrongPasswordRejected() {
        try { repo.login(Repository.ADMIN_EMAIL, "salah"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Password")); }
    }

    @Test public void duplicateContactRejected() throws Exception {
        member("Siti Aminah", "081234567890", null);
        try { member("Siti Lain", "081234567890", null); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("sudah terdaftar")); }
    }

    @Test public void invalidReferralCodeRejected() throws Exception {
        try { member("Orang Uji", "081234567899", "ABCDEF"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("6 digit")); }
    }

    @Test public void unknownReferralCodeRejected() throws Exception {
        try { member("Orang Uji", "081234567899", "999999"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("tidak ditemukan")); }
    }

    @Test public void registrationLinksSingleLevelReferral() throws Exception {
        Models.User inviter = member("Siti Aminah", "081234567890", null);
        Models.User invited = member("Budi", "081234567891", inviter.referralId);
        assertEquals(inviter.userId, invited.referredBy);
        assertEquals(1, repo.allReferrals().size());
        assertEquals("PENDING", repo.allReferrals().get(0).status);
    }

    /* ---------------- Bab 4: poin harian ---------------- */

    @Test public void checkinGivesPointsOnlyOncePerDay() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.checkin(u.userId);
        assertEquals(Config.DEFAULT_CHECKIN_POINTS, repo.user(u.userId).points);
        try { repo.checkin(u.userId); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Sudah check-in")); }
        assertEquals(Config.DEFAULT_CHECKIN_POINTS, repo.user(u.userId).points);
    }

    @Test public void rewardedAdsCappedAtTwentyPerDay() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);
        assertEquals(100, repo.user(u.userId).points);
        try { repo.watchAd(u.userId); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Batas iklan harian")); }
        assertEquals(20, repo.adsToday(u.userId));
        assertEquals(20, repo.adRewards().size());
    }

    /* ---------------- Bab 8: konversi poin ---------------- */

    @Test public void conversionTenThousandPointsEqualsOneThousandRupiah() {
        assertEquals(1000, repo.pointsToRupiah(10000));
        assertEquals(500000, repo.rupiahToPoints(50000));
        assertEquals(50000, repo.pointsToRupiah(500000));
    }

    /* ---------------- Bab 3: bonus referral satu tingkat ---------------- */

    @Test public void referralBonusPaidOnlyAfterQualifyingOrder() throws Exception {
        Models.User inviter = member("Siti Aminah", "081234567890", null);
        Models.User invited = member("Budi", "081234567891", inviter.referralId);

        Models.Order o = buy(invited, "PRD-HBA-001", 1);
        assertTrue(o.total >= 50000);
        assertEquals(0, repo.user(inviter.userId).points);

        repo.markStatus(o, "PAID", "USR-ADMIN", "verifikasi bayar");

        assertEquals(Config.DEFAULT_REFERRAL_BONUS, repo.user(inviter.userId).points);
        assertTrue(repo.user(invited.userId).verified);
        assertEquals("VERIFIED", repo.allReferrals().get(0).status);
        assertEquals(500, repo.user(invited.userId).points); // poin belanja pembeli (HBA-001 = 500 poin/unit)
    }

    @Test public void orderBelowMinimumDoesNotPayReferralBonus() throws Exception {
        Models.Settings s = repo.settings();
        s.referralMinOrder = 10_000_000L;
        repo.saveSettings(s, null);

        Models.User inviter = member("Siti Aminah", "081234567890", null);
        Models.User invited = member("Budi", "081234567891", inviter.referralId);
        Models.Order o = buy(invited, "PRD-HBA-001", 1);
        repo.markStatus(o, "PAID", "USR-ADMIN", "");

        assertEquals(0, repo.user(inviter.userId).points);
        assertEquals("PENDING", repo.allReferrals().get(0).status);
    }

    @Test public void noChainedBonusForSecondLevelReferral() throws Exception {
        Models.User a = member("Siti Aminah", "081234567890", null);
        Models.User b = member("Budi", "081234567891", a.referralId);
        Models.User c = member("Citra", "081234567892", b.referralId);

        Models.Order o = buy(c, "PRD-HBA-001", 1);
        repo.markStatus(o, "PAID", "USR-ADMIN", "");

        assertEquals(0, repo.user(a.userId).points);
        assertEquals(Config.DEFAULT_REFERRAL_BONUS, repo.user(b.userId).points);
    }

    @Test public void purchasePointsGrantedOnceAndReversibleOnRefund() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 2);
        assertEquals(170000, o.subtotal); // 2 x Rp85.000 (harga promo)
        assertEquals(1000, o.items.get(0).points); // 2 x 500 poin

        repo.markStatus(o, "PAID", "USR-ADMIN", "bayar");
        repo.markStatus(o, "PROCESSING", "USR-ADMIN", "lanjut");
        repo.markStatus(o, "SHIPPED", "USR-ADMIN", "kirim");
        assertEquals(1000, repo.user(u.userId).points); // tidak dobel

        repo.markStatus(o, "REFUNDED", "USR-ADMIN", "refund");
        assertEquals(0, repo.user(u.userId).points);
        assertEquals(50, repo.product("PRD-HBA-001").stock); // stok dikembalikan
    }

    @Test public void refundAlsoReversesReferralBonus() throws Exception {
        Models.User inviter = member("Siti Aminah", "081234567890", null);
        Models.User invited = member("Budi", "081234567891", inviter.referralId);
        Models.Order o = buy(invited, "PRD-HBA-001", 1);
        repo.markStatus(o, "PAID", "USR-ADMIN", "");
        assertEquals(Config.DEFAULT_REFERRAL_BONUS, repo.user(inviter.userId).points);

        repo.markStatus(o, "REFUNDED", "USR-ADMIN", "komplain");
        assertEquals(0, repo.user(inviter.userId).points);
        assertEquals("CANCELLED", repo.allReferrals().get(0).status);
        assertFalse(repo.user(invited.userId).verified);
    }

    /* ---------------- Bab 5 & 9: withdrawal ---------------- */

    @Test public void withdrawalBlockedUntilAllGatesPassed() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        assertFalse(repo.eligibility(u).ok);

        repo.checkin(u.userId);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);
        u = repo.user(u.userId);
        assertEquals(110, u.points);
        assertFalse("saldo masih di bawah minimum", repo.eligibility(u).ok);

        try { repo.requestWithdrawal(u, 110, "DANA", "Siti Aminah", "081234567890"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("belum terpenuhi")); }
    }

    @Test public void withdrawalSuccessDeductsPointsAndRejectionRefunds() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.addPoints(u.userId, 600000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);

        Models.User fresh = repo.user(u.userId);
        assertTrue(repo.eligibility(fresh).ok);

        Models.Withdrawal w = repo.requestWithdrawal(fresh, 500000, "DANA", "Siti Aminah", "081234567890");
        assertEquals(50000, w.amountRupiah);
        assertEquals("PENDING", w.status);
        assertEquals(600100 - 500000, repo.user(u.userId).points);

        repo.processWithdrawal(w.withdrawalId, "REJECTED", "USR-ADMIN", "rekening salah");
        assertEquals(600100, repo.user(u.userId).points);
        assertEquals("REJECTED", repo.allWithdrawals().get(0).status);
    }

    @Test public void withdrawalPaidMarksLedger() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.addPoints(u.userId, 600000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);
        Models.Withdrawal w = repo.requestWithdrawal(repo.user(u.userId), 500000, "BCA", "Siti Aminah", "1234567890");
        repo.processWithdrawal(w.withdrawalId, "PAID", "USR-ADMIN", "transfer");

        boolean paid = false;
        for (Models.Ledger l : repo.ledger(u.userId, 0)) if ("WITHDRAW_PAID".equals(l.type)) paid = true;
        assertTrue(paid);
    }

    @Test public void secondWithdrawalSameDayRejected() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.addPoints(u.userId, 2000000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);

        repo.requestWithdrawal(repo.user(u.userId), 500000, "DANA", "Siti Aminah", "081234567890");
        try {
            repo.requestWithdrawal(repo.user(u.userId), 500000, "DANA", "Siti Aminah", "081234567890");
            fail("harus gagal");
        } catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("belum terpenuhi")); }
    }

    @Test public void rejectedWithdrawalDoesNotCountTowardDailyLimit() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.addPoints(u.userId, 2000000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);

        Models.Withdrawal w = repo.requestWithdrawal(repo.user(u.userId), 500000, "DANA", "Siti Aminah", "081234567890");
        repo.processWithdrawal(w.withdrawalId, "REJECTED", "USR-ADMIN", "gagal");
        Models.Withdrawal again = repo.requestWithdrawal(repo.user(u.userId), 500000, "DANA", "Siti Aminah", "081234567890");
        assertEquals("PENDING", again.status);
    }

    /* ---------------- Bab 6: order & stok ---------------- */

    @Test public void stockCannotBeOverSold() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try { repo.cartAdd("PRD-HBA-001", 999); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Stok tidak mencukupi")); }
    }

    @Test public void orderRequiresCompleteShippingData() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.cartAdd("PRD-HBA-001", 1);
        try { repo.createOrder(u, "", "", "", "", "", ""); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Lengkapi")); }
    }

    @Test public void orderNumberIsSequentialAndStockDecremented() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 3);
        assertEquals("ORD-" + Util.orderDay() + "-000001", o.orderNumber);
        assertEquals("WAITING_PAYMENT", o.orderStatus);
        assertEquals("UNPAID", o.paymentStatus);
        assertEquals(47, repo.product("PRD-HBA-001").stock);
        assertEquals(0, repo.cart().size());
        assertEquals(3, o.items.get(0).qty);
    }

    @Test public void cartTotalsIncludeShippingAndFreeShippingRule() throws Exception {
        member("Siti Aminah", "081234567890", null);
        repo.cartAdd("PRD-HBA-003", 1); // Rp79.000 harga promo
        assertEquals(79000, repo.cartSubtotal());
        assertEquals(15000, repo.cartShipping());
        assertEquals(94000, repo.cartTotal());

        Models.Settings s = repo.settings();
        s.shippingFlat = 20000; s.freeShippingMin = 75000;
        repo.saveSettings(s, null);
        assertEquals(0, repo.cartShipping());
        assertEquals(79000, repo.cartTotal());
    }

    @Test public void emptyCartCannotCheckout() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try { repo.createOrder(u, "Siti", "081234567890", "Jl. A", "Karawang", "41361", ""); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Keranjang kosong")); }
    }

    /* ---------------- Bab 7: dashboard admin ---------------- */

    @Test public void dashboardStatsReflectReality() throws Exception {
        Models.User inviter = member("Siti Aminah", "081234567890", null);
        Models.User invited = member("Budi", "081234567891", inviter.referralId);
        Models.Order o = buy(invited, "PRD-HBA-002", 1); // 120.000, 600 poin

        Repository.Stats s = repo.stats();
        assertEquals(2, s.totalMember);
        assertEquals(3, s.totalProducts);
        assertEquals(1, s.totalOrders);
        assertEquals(1, s.orderPending);
        assertEquals(1, s.totalReferral);
        assertEquals(0, s.verifiedReferral);
        assertEquals(0, s.withdrawalPending);

        repo.markStatus(o, "PAID", "USR-ADMIN", "");
        s = repo.stats();
        assertEquals(1, s.verifiedReferral);
        assertEquals(1, s.verifiedMember);
        assertEquals(Config.DEFAULT_REFERRAL_BONUS + 600, s.totalPoints);
        assertEquals(repo.pointsToRupiah(5600), s.totalBalance);
    }

    @Test public void stockMovementRecordedForOrder() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        buy(u, "PRD-HBA-001", 2);
        assertFalse(repo.stockMovements().isEmpty());
        assertEquals(-2, repo.stockMovements().get(0).optInt("delta"));
        assertEquals(48, repo.stockMovements().get(0).optInt("stockAfter"));
    }

    /* ---------------- Bab 13: keamanan, audit, fraud ---------------- */

    @Test public void adminActionsAreAudited() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        int before = repo.adminLogs(0).size();
        repo.adminAdjustBalance(u.userId, 5000, "bonus kampanye", "USR-ADMIN");
        assertEquals(before + 1, repo.adminLogs(0).size());
        assertEquals("SALDO_ADJUST", repo.adminLogs(1).get(0).optString("action"));
        assertEquals(5000, repo.user(u.userId).points);
    }

    @Test public void adminAdjustRequiresReason() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try { repo.adminAdjustBalance(u.userId, 100, "", "USR-ADMIN"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Alasan")); }
    }

    @Test public void fraudFlagBlocksWithdrawal() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.addPoints(u.userId, 600000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);
        repositoryEligibleTrue(u);
        repo.setFraud(u.userId, true, "USR-ADMIN");
        assertFalse(repo.eligibility(repo.user(u.userId)).ok);
    }

    private void repositoryEligibleTrue(Models.User u) {
        assertTrue(repo.eligibility(repo.user(u.userId)).ok);
    }

    @Test public void inactiveAccountCannotLogin() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.setUserStatus(u.userId, "SUSPENDED", "USR-ADMIN");
        try { repo.login("081234567890", "rahasia1"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("tidak aktif")); }
    }

    @Test public void passwordIsNotStoredInPlainText() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        assertFalse("rahasia1".equals(u.passwordHash));
        assertEquals(64, u.passwordHash.length());
        assertFalse(u.salt.isEmpty());
    }

    /* ---------------- QRIS ---------------- */

    @Test public void qrisPayloadCarriesAmountAndValidCrc() {
        String p = QrisGenerator.build(Config.QRIS_BASE, 100000);
        assertEquals("100000", QrisGenerator.amountOf(p));
        assertTrue(QrisGenerator.validate(p));
        assertFalse(QrisGenerator.merchantNameOf(p).isEmpty());
    }

    @Test public void qrisCrcChangesWithAmountAndStaysValid() {
        String a = QrisGenerator.build(Config.QRIS_BASE, 100000);
        String b = QrisGenerator.build(Config.QRIS_BASE, 100001);
        assertFalse(a.equals(b));
        assertTrue(QrisGenerator.validate(a));
        assertTrue(QrisGenerator.validate(b));
    }

    @Test public void tamperedQrisFailsChecksum() {
        String p = QrisGenerator.build(Config.QRIS_BASE, 50000);
        String tampered = p.substring(0, p.length() - 6) + "0000" + p.substring(p.length() - 2);
        assertFalse(QrisGenerator.validate(tampered));
    }

    /* ---------------- Validasi form ---------------- */

    @Test public void weakPasswordRejected() {
        try { repo.register("Siti Aminah", "081234567890", "123", null); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Password minimal")); }
    }

    @Test public void invalidPhoneRejected() {
        try { repo.register("Siti Aminah", "12345", "rahasia1", null); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Nomor HP")); }
    }

    @Test public void emailRegistrationWorks() throws Exception {
        Models.User u = member("Siti Aminah", "siti@contoh.com", null);
        assertEquals("siti@contoh.com", u.email);
        assertNotNull(repo.login("siti@contoh.com", "rahasia1"));
    }

    @Test public void referralLookupFindsOnlyRealCodes() {
        assertNull(repo.findByReferralId("999999"));
        assertNotNull(repo.findByReferralId("000001"));
    }

    @Test public void seedingIsIdempotentAcrossRestarts() {
        Repository again = Repository.with(repo.store());
        assertEquals(3, again.allProducts().size());
        assertEquals(1, again.allUsers().size()); // hanya admin
    }

    /* ---------------- Validasi form ---------------- */

    /* ---------------- Ubah password ---------------- */

    @Test public void changePasswordAcceptsCorrectOldPassword() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.changePassword(u.userId, "rahasia1", "rahasia2", "rahasia2");
        assertNotNull(repo.login("081234567890", "rahasia2"));
    }

    @Test public void changePasswordRejectsWrongOldPassword() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try {
            repo.changePassword(u.userId, "salah", "rahasia2", "rahasia2");
            fail("harus gagal");
        } catch (Repository.RuleException e) {
            assertTrue(e.getMessage().contains("Password lama salah"));
        }
        assertNotNull(repo.login("081234567890", "rahasia1"));
    }

    @Test public void changePasswordRejectsMismatchedConfirmation() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try {
            repo.changePassword(u.userId, "rahasia1", "rahasia2", "rahasia3");
            fail("harus gagal");
        } catch (Repository.RuleException e) {
            assertTrue(e.getMessage().contains("Konfirmasi"));
        }
    }

    @Test public void changePasswordRejectsShortNewPassword() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try {
            repo.changePassword(u.userId, "rahasia1", "123", "123");
            fail("harus gagal");
        } catch (Repository.RuleException e) {
            assertTrue(e.getMessage().contains("minimal 6 karakter"));
        }
    }

    @Test public void changePasswordRejectsReusingOldPassword() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try {
            repo.changePassword(u.userId, "rahasia1", "rahasia1", "rahasia1");
            fail("harus gagal");
        } catch (Repository.RuleException e) {
            assertTrue(e.getMessage().contains("berbeda"));
        }
    }

    @Test public void changePasswordRotatesSaltSoOldHashNoLongerMatches() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        String saltBefore = u.salt;
        String hashBefore = u.passwordHash;
        repo.changePassword(u.userId, "rahasia1", "rahasia2", "rahasia2");
        Models.User after = repo.user(u.userId);
        assertFalse("salt harus diperbarui", saltBefore.equals(after.salt));
        assertFalse("hash harus berubah", hashBefore.equals(after.passwordHash));
    }

    @Test public void adminCanChangeSeededPassword() throws Exception {
        Models.User admin = repo.login(Repository.ADMIN_EMAIL, Repository.ADMIN_PASSWORD);
        repo.changePassword(admin.userId, Repository.ADMIN_PASSWORD, "adminBaru1", "adminBaru1");
        assertNotNull(repo.login(Repository.ADMIN_EMAIL, "adminBaru1"));
        try { repo.login(Repository.ADMIN_EMAIL, Repository.ADMIN_PASSWORD); fail("password lama harus tidak berlaku"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Password salah")); }
    }

    @Test public void changePasswordIsRecordedInAuditLog() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.changePassword(u.userId, "rahasia1", "rahasia2", "rahasia2");
        boolean found = false;
        for (org.json.JSONObject o : repo.adminLogs(0)) {
            if ("UBAH_PASSWORD".equals(o.optString("action"))) { found = true; break; }
        }
        assertTrue("perubahan password harus tercatat pada audit log", found);
    }

    /* ---------------- Kelola produk, penarikan, dan poin oleh admin ---------------- */

    @Test public void produkMenyimpanGambarPoinDeskripsiDanStok() throws Exception {
        Models.Product p = repo.product("PRD-HBA-001");
        p.imageUrl = "https://contoh.id/gambar.jpg";
        p.description = "Deskripsi baru untuk pengujian";
        p.points = 750;
        p.stock = 42;
        repo.saveProduct(p, "USR-ADMIN");

        Models.Product saved = Models.Product.from(repo.product("PRD-HBA-001").toJson().toString());
        assertEquals("https://contoh.id/gambar.jpg", saved.imageUrl);
        assertEquals("Deskripsi baru untuk pengujian", saved.description);
        assertEquals(750, saved.points);
        assertEquals(42, saved.stock);
    }

    @Test public void urlGambarHarusHttpAtauHttps() {
        assertTrue(Util.isHttpUrl("https://contoh.id/a.jpg"));
        assertTrue(Util.isHttpUrl("http://contoh.id/a.jpg"));
        assertFalse(Util.isHttpUrl("ftp://contoh.id/a.jpg"));
        assertFalse(Util.isHttpUrl("javascript:alert(1)"));
        assertFalse(Util.isHttpUrl(""));
        assertFalse(Util.isHttpUrl(null));
    }

    @Test public void penarikanHanyaMenerimaMetodeDropdown() throws Exception {
        Models.User u = siapTarik();
        for (String salah : new String[]{"", "  ", "BITCOIN", "bca", "OVO2", "DANA BANK"}) {
            try {
                repo.requestWithdrawal(u, 500000, salah, "Siti Aminah", "081234567890");
                fail("metode di luar daftar harus gagal: [" + salah + "]");
            } catch (Repository.RuleException e) {
                assertTrue(e.getMessage().contains("Pilih metode"));
            }
        }
        // Spasi di tepi dirapikan, bukan dianggap metode lain.
        Models.Withdrawal spasi = repo.requestWithdrawal(u, 500000, "  DANA  ", "Siti Aminah", "081234567890");
        assertEquals("DANA", spasi.method);
        repo.processWithdrawal(spasi.withdrawalId, "REJECTED", "USR-ADMIN", "uji");

        for (String benar : Config.WITHDRAW_METHODS) {
            String tujuan = Config.isEwallet(benar) ? "081234567890" : "1234567890";
            Models.Withdrawal w = repo.requestWithdrawal(u, 500000, benar, "Siti Aminah", tujuan);
            assertEquals(benar, w.method);
            assertEquals("Siti Aminah", w.accountName);
            assertEquals("PENDING", w.status);
            repo.processWithdrawal(w.withdrawalId, "REJECTED", "USR-ADMIN", "uji");
        }
    }

    @Test public void penarikanMewajibkanNamaPemilikRekening() throws Exception {
        Models.User u = siapTarik();
        try { repo.requestWithdrawal(u, 500000, "DANA", "", "081234567890"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Nama pemilik")); }
        try { repo.requestWithdrawal(u, 500000, "DANA", "Al", "081234567890"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Nama pemilik")); }
    }

    @Test public void penarikanMemvalidasiNomorSesuaiJenisMetode() throws Exception {
        Models.User u = siapTarik();
        try { repo.requestWithdrawal(u, 500000, "DANA", "Siti Aminah", "12345"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Nomor HP")); }
        try { repo.requestWithdrawal(u, 500000, "BCA", "Siti Aminah", "0812"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("rekening BCA")); }
    }

    @Test public void adminDapatMenetapkanPoinMemberKeNilaiTertentu() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.adminSetPoints(u.userId, 12345, "koreksi saldo", "USR-ADMIN");
        assertEquals(12345, repo.user(u.userId).points);

        repo.adminSetPoints(u.userId, 100, "penyesuaian turun", "USR-ADMIN");
        assertEquals(100, repo.user(u.userId).points);

        assertNotEquals(0, repo.adminLogs(0).size());
        boolean tercatat = false;
        for (org.json.JSONObject o : repo.adminLogs(0)) {
            if ("POIN_SET".equals(o.optString("action"))) tercatat = true;
        }
        assertTrue("penetapan poin harus tercatat di audit log", tercatat);
    }

    @Test public void penetapanPoinMenolakNilaiTidakSah() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        try { repo.adminSetPoints(u.userId, -1, "koreksi", "USR-ADMIN"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("negatif")); }
        try { repo.adminSetPoints(u.userId, 10, "ab", "USR-ADMIN"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Alasan")); }
        try { repo.adminSetPoints(u.userId, 0, "koreksi", "USR-ADMIN"); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("sudah bernilai")); }
    }

    @Test public void pembeliDapatMengirimDataTransferUntukDiverifikasi() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 1);
        assertEquals("UNPAID", o.paymentStatus);

        Models.Order updated = repo.submitPayment(o.orderId, "Budi Pengirim", o.total, "BCA", "transfer 09:00");
        assertEquals("VERIFYING", updated.paymentStatus);
        assertEquals("WAITING_PAYMENT", updated.orderStatus);
        assertEquals("Budi Pengirim", updated.buyerName);
        assertEquals(o.total, updated.paidAmount);
        assertEquals(Boolean.TRUE, updated.paidMatches());
    }

    @Test public void kesesuaianNominalTerdeteksiSaatTidakSamaDanSaatBelumDiisi() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 1);

        assertNull("belum diisi harus null, bukan false", o.paidMatches());

        repo.submitPayment(o.orderId, "Budi Pengirim", o.total - 5000, "BCA", "");
        Models.Order kurang = repo.order(o.orderId);
        assertEquals(Boolean.FALSE, kurang.paidMatches());

        repo.submitPayment(o.orderId, "Budi Pengirim", o.total, "BCA", "");
        assertEquals(Boolean.TRUE, repo.order(o.orderId).paidMatches());
    }

    @Test public void dataTransferTidakSahDitolak() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 1);
        try { repo.submitPayment(o.orderId, "Ab", o.total, "", ""); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Nama pengirim")); }
        try { repo.submitPayment(o.orderId, "Budi Pengirim", 0, "", ""); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("Nominal")); }
    }

    @Test public void dataTransferTidakBolehDiubahSetelahLunas() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 1);
        repo.submitPayment(o.orderId, "Budi Pengirim", o.total, "BCA", "");
        repo.markStatus(repo.order(o.orderId), "PAID", "USR-ADMIN", "dana masuk");
        try { repo.submitPayment(o.orderId, "Budi Pengirim", o.total, "BCA", ""); fail("harus gagal"); }
        catch (Repository.RuleException e) { assertTrue(e.getMessage().contains("sudah terverifikasi")); }
    }

    @Test public void verifikasiMembuatPoinDanPesananLanjut() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 1);
        repo.submitPayment(o.orderId, "Budi Pengirim", o.total, "BCA", "");
        repo.markStatus(repo.order(o.orderId), "PAID", "USR-ADMIN", "sesuai mutasi");
        assertEquals(500, repo.user(u.userId).points); // 1 x 500 poin produk
        assertEquals("PAID", repo.order(o.orderId).paymentStatus);
    }

    @Test public void adminDapatMenambahProdukBaruDanStokAwalnyaTercatat() throws Exception {
        Models.Product p = new Models.Product();
        p.productId = "PRD-HBA-004";
        p.sku = "HBA-004";
        p.name = "Herbal Diet D";
        p.category = "Herbal Diet";
        p.description = "Produk baru untuk pengujian";
        p.imageUrl = "https://contoh.id/hba-004.jpg";
        p.price = 95000;
        p.points = 600;
        // Mengikuti alur form: produk disimpan dengan stok 0, lalu stok awal
        // dimasukkan lewat adjustStock agar tercatat di riwayat.
        p.stock = 0;
        p.weight = 100;
        p.status = "ACTIVE";
        repo.saveProduct(p, "USR-ADMIN");
        repo.adjustStock(p.productId, 25, "Stok awal produk baru", "USR-ADMIN");

        Models.Product saved = repo.product("PRD-HBA-004");
        assertNotNull(saved);
        assertEquals("Herbal Diet D", saved.name);
        assertEquals("https://contoh.id/hba-004.jpg", saved.imageUrl);
        assertEquals(25, saved.stock);
        assertEquals(600, saved.points);

        boolean tercatat = false;
        for (org.json.JSONObject m : repo.stockMovements()) {
            if ("PRD-HBA-004".equals(m.optString("productId"))
                    && "Stok awal produk baru".equals(m.optString("reason"))) tercatat = true;
        }
        assertTrue("stok awal produk baru harus tercatat di riwayat", tercatat);

        boolean logAda = false;
        for (org.json.JSONObject o : repo.adminLogs(0)) {
            if ("PRODUCT_CREATE".equals(o.optString("action"))) logAda = true;
        }
        assertTrue("penambahan produk harus tercatat di audit log", logAda);
    }

    @Test public void produkBaruMunculDiKatalogYangDilihatMember() throws Exception {
        int sebelum = repo.activeProducts().size();
        Models.Product p = new Models.Product();
        p.productId = "PRD-HBA-009";
        p.sku = "HBA-009";
        p.name = "Herbal Diet E";
        p.category = "Herbal Diet";
        p.description = "Produk uji katalog";
        p.price = 88000;
        p.points = 400;
        p.stock = 10;
        p.status = "ACTIVE";
        repo.saveProduct(p, "USR-ADMIN");
        assertEquals(sebelum + 1, repo.activeProducts().size());
    }

    @Test public void pembatalanBerulangTidakMengembalikanStokDuaKali() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 3);
        assertEquals(47, repo.product("PRD-HBA-001").stock);

        repo.markStatus(repo.order(o.orderId), "CANCELLED", "USR-ADMIN", "pembeli batal");
        assertEquals(50, repo.product("PRD-HBA-001").stock);

        // Mengulang aksi yang sama tidak boleh menambah stok lagi.
        repo.markStatus(repo.order(o.orderId), "CANCELLED", "USR-ADMIN", "klik ulang");
        assertEquals(50, repo.product("PRD-HBA-001").stock);

        // Berpindah ke REFUNDED juga tidak memulihkan stok untuk kedua kalinya.
        repo.markStatus(repo.order(o.orderId), "REFUNDED", "USR-ADMIN", "refund");
        assertEquals(50, repo.product("PRD-HBA-001").stock);
        assertEquals("REFUNDED", repo.order(o.orderId).paymentStatus);
    }

    @Test public void pembatalanBerulangTidakMemotongPoinDuaKali() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 2);
        repo.submitPayment(o.orderId, "Budi Pengirim", o.total, "BCA", "");
        repo.markStatus(repo.order(o.orderId), "PAID", "USR-ADMIN", "lunas");
        assertEquals(1000, repo.user(u.userId).points);

        repo.markStatus(repo.order(o.orderId), "REFUNDED", "USR-ADMIN", "refund");
        assertEquals(0, repo.user(u.userId).points);

        repo.markStatus(repo.order(o.orderId), "REFUNDED", "USR-ADMIN", "klik ulang");
        assertEquals("poin tidak boleh menjadi negatif", 0, repo.user(u.userId).points);
    }

    @Test public void pembatalanTercatatSekaliPadaAuditLog() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        Models.Order o = buy(u, "PRD-HBA-001", 1);
        repo.markStatus(repo.order(o.orderId), "CANCELLED", "USR-ADMIN", "batal");
        repo.markStatus(repo.order(o.orderId), "CANCELLED", "USR-ADMIN", "batal lagi");

        int catatan = 0;
        for (org.json.JSONObject log : repo.adminLogs(0)) {
            if ("ORDER_STATUS".equals(log.optString("action"))
                    && log.optString("target").contains(o.orderNumber)
                    && log.optString("data").contains("CANCELLED")) catatan++;
        }
        assertEquals("status akhir hanya dicatat sekali", 1, catatan);
    }

    /** Menyiapkan member yang seluruh syarat penarikannya sudah terpenuhi. */
    private Models.User siapTarik() throws Exception {
        Models.User u = member("Siti Aminah", "081234567890", null);
        repo.addPoints(u.userId, 600000, "ADMIN_CREDIT", "saldo uji", null);
        for (int i = 0; i < 20; i++) repo.watchAd(u.userId);
        return repo.user(u.userId);
    }
}