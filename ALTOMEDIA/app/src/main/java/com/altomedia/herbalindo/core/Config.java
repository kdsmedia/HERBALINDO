package com.altomedia.herbalindo.core;

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
    public static final long DEFAULT_MIN_WITHDRAW = 50000L;
    public static final int DEFAULT_MAX_WITHDRAW_PER_DAY = 1;
    public static final long DEFAULT_SHIPPING_FLAT = 15000L;

    // QRIS merchant (statis) — nominal ditambahkan dinamis via tag 54 + CRC16
    public static final String QRIS_BASE =
        "00020101021126610014COM.GO-JEK.WWW01189360091439663050810210G9663050810303UMI51440014ID.CO.QRIS.WWW0215ID10254671365660303UMI5204549953033605802ID5917ALTOMEDIA, Grosir6008KARAWANG61054136162070703A016304D21A";

    // AdMob. Unit test resmi Google agar build tidak memicu pelanggaran saat QA.
    public static final String ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713";
    public static final String ADMOB_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111";
    public static final String ADMOB_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917";

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
    public static final String C_WITHDRAWALS = "withdrawals";
    public static final String C_SETTINGS = "settings";
    public static final String C_ADMIN_LOGS = "admin_logs";
    public static final String C_STOCK_MOVEMENTS = "stock_movements";

    public static final String[] ORDER_FLOW = {
        "PENDING", "WAITING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "COMPLETED"
    };

    public static String paymentLabel(String status) {
        switch (status) {
            case "UNPAID": return "BELUM DIBAYAR";
            case "PAID": return "LUNAS";
            case "REFUNDED": return "DIKEMBALIKAN";
            case "FAILED": return "GAGAL";
            default: return status;
        }
    }

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