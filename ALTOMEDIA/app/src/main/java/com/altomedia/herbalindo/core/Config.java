package com.altomedia.herbalindo.core;

import com.altomedia.herbalindo.BuildConfig;

/**
 * Konstanta aplikasi HERBALINDO.
 * Nilai default mengikuti Bab 8, 9, dan 15 pada dokumen spesifikasi.
 */
public final class Config {
    private Config() {}

    public static final String APP_NAME = "HERBALINDO";
    public static final String DEVELOPER = "ALTOMEDIA";
    public static final String CONTACT_EMAIL = "altomediaindonesia@gmail.com";

    // Konversi poin: 10.000 poin = Rp1.000
    public static final int POINTS_PER_UNIT = 10000;
    public static final int RUPIAH_PER_UNIT = 1000;

    // Default aturan bisnis (dapat diubah Admin pada menu Pengaturan)
    public static final int DEFAULT_CHECKIN_POINTS = 10;
    public static final int DEFAULT_AD_POINTS = 5;
    public static final int DEFAULT_AD_MAX_PER_DAY = 20;
    public static final int DEFAULT_REFERRAL_BONUS = 5000;
    public static final long DEFAULT_REFERRAL_MIN_ORDER = 50000L;
    public static final long DEFAULT_MIN_WITHDRAW = 100L;

    /*
     * Nominal penarikan saldo yang tersedia. Member hanya memilih salah satu
     * nilai ini; isian bebas dihapus agar jumlah yang dicairkan selalu sama
     * dengan nilai yang disetujui admin.
     */
    public static final long[] WITHDRAW_OPTIONS_RUPIAH = {
            100L, 200L, 500L, 1000L, 2000L, 5000L, 10000L, 20000L};
    public static final int DEFAULT_MAX_WITHDRAW_PER_DAY = 1;
    public static final long DEFAULT_SHIPPING_FLAT = 15000L;

    /*
     * Level akun. Poin (rupiah) dan XP (naik level) dipisah: poin dipakai untuk
     * saldo dan withdrawal, XP hanya mengukur keaktifan. XP tidak pernah
     * berkurang sehingga level anggota tidak turun saat pesanan dibatalkan.
     *
     * Tiga jalur kenaikan sesuai kebutuhan bisnis:
     *   - Pembelian  : jalur tercepat, 1 XP per Rp1.000 nilai pesanan.
     *   - Undang teman: 250 XP, baru dihitung setelah pesanan pertama teman lunas.
     *   - Aktif harian: 15 XP check-in plus 5 XP per hari beruntun, dan 5 XP
     *                   setiap iklan berhadiah yang ditonton sampai selesai.
     */
    public static final long XP_PER_RUPIAH_UNIT = 1000L;
    public static final long XP_PURCHASE_PER_UNIT = 1L;
    public static final long XP_REFERRAL = 250L;
    public static final long XP_DAILY_CHECKIN = 15L;
    public static final long XP_DAILY_STREAK = 5L;
    public static final long XP_AD = 5L;
    public static final long XP_STREAK_MAX_DAYS = 30L;
    public static final long XP_LEVEL_BASE = 100L;

    // QRIS merchant (statis) — nominal ditambahkan dinamis via tag 54 + CRC16
    public static final String QRIS_BASE =
        "00020101021126610014COM.GO-JEK.WWW01189360091439663050810210G9663050810303UMI51440014ID.CO.QRIS.WWW0215ID10254671365660303UMI5204549953033605802ID5917ALTOMEDIA, Grosir6008KARAWANG61054136162070703A016304D21A";

    // AdMob — unit produksi milik akun ALTOMEDIA.
    public static final String ADMOB_APP_ID_PROD = "ca-app-pub-6881903056221433~4194258778";
    public static final String ADMOB_BANNER_UNIT_PROD = "ca-app-pub-6881903056221433/9657593588";
    public static final String ADMOB_REWARDED_UNIT_PROD = "ca-app-pub-6881903056221433/4720872385";
    public static final String ADMOB_INTERSTITIAL_UNIT_PROD = "ca-app-pub-6881903056221433/3693811302";

    // Unit uji resmi Google. Build debug memakai unit ini agar penayangan saat
    // pengembangan tidak tercatat sebagai klik tidak sah pada akun produksi.
    public static final String ADMOB_BANNER_UNIT_TEST = "ca-app-pub-3940256099942544/6300978111";
    public static final String ADMOB_REWARDED_UNIT_TEST = "ca-app-pub-3940256099942544/5224354917";
    public static final String ADMOB_INTERSTITIAL_UNIT_TEST = "ca-app-pub-3940256099942544/1033173712";

    /** Build debug memakai unit uji; build rilis memakai unit produksi. */
    public static String adUnit(String prod, String test) {
        return BuildConfig.DEBUG ? test : prod;
    }

    public static String bannerUnit() { return adUnit(ADMOB_BANNER_UNIT_PROD, ADMOB_BANNER_UNIT_TEST); }

    public static String rewardedUnit() { return adUnit(ADMOB_REWARDED_UNIT_PROD, ADMOB_REWARDED_UNIT_TEST); }

    public static String interstitialUnit() {
        return adUnit(ADMOB_INTERSTITIAL_UNIT_PROD, ADMOB_INTERSTITIAL_UNIT_TEST);
    }

    public static final String PREFS = "herbalindo_prefs";
    public static final String DB_NAME = "herbalindo.db";
    public static final int DB_VERSION = 1;

    /* Koleksi mengikuti penamaan Bab 12 agar mudah dipetakan ke Firestore. */
    public static final String C_USERS = "users";
    public static final String C_PRODUCTS = "products";
    public static final String C_CATEGORIES = "categories";
    public static final String C_ORDERS = "orders";
    public static final String C_ORDER_ITEMS = "order_items";
    public static final String C_PAYMENTS = "payments";
    public static final String C_POINTS_LEDGER = "points_ledger";
    public static final String C_REFERRALS = "referrals";
    public static final String C_AD_REWARDS = "ad_rewards";
    public static final String C_DAILY_TASKS = "daily_tasks";
    public static final String C_XP_EVENTS = "xp_events";
    public static final String C_WITHDRAWALS = "withdrawals";
    public static final String C_SETTINGS = "settings";
    public static final String C_ADMIN_LOGS = "admin_logs";
    public static final String C_STOCK_MOVEMENTS = "stock_movements";

    /** Metode pencairan saldo yang tersedia (dropdown, bukan isian bebas). */
    public static final String[] WITHDRAW_METHODS = {"DANA", "OVO", "GOPAY", "BCA"};

    /** Metode pencairan berjenis dompet digital memakai nomor HP sebagai tujuan. */
    public static boolean isEwallet(String method) {
        return "DANA".equals(method) || "OVO".equals(method) || "GOPAY".equals(method);
    }

    /** Status pembayaran. VERIFYING dipakai saat pembeli mengirim data transfer. */
    public static String paymentLabel(String status) {
        switch (status) {
            case "UNPAID": return "BELUM DIBAYAR";
            case "VERIFYING": return "MENUNGGU VERIFIKASI";
            case "PAID": return "LUNAS";
            case "REFUNDED": return "DIKEMBALIKAN";
            case "FAILED": return "GAGAL";
            default: return status;
        }
    }

    public static final String[] ORDER_FLOW = {
        "PENDING", "WAITING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "COMPLETED"
    };

    public static String orderLabel(String status) {
        switch (status) {
            case "PENDING": return "TERTUNDA";
            case "WAITING_PAYMENT": return "MENUNGGU PEMBAYARAN";
            case "PAID": return "DIBAYAR";
            case "PROCESSING": return "DIPROSES";
            case "SHIPPED": return "DIKIRIM";
            case "DELIVERED": return "DITERIMA";
            case "COMPLETED": return "SELESAI";
            case "CANCELLED": return "DIBATALKAN";
            case "REFUNDED": return "DIKEMBALIKAN";
            default: return status;
        }
    }
}