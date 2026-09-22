package com.altomedia.herbalindo.level;

import com.altomedia.herbalindo.core.Config;

/**
 * Kurva level akun HERBALINDO.
 *
 * <p>Level 1 adalah titik awal. Untuk naik dari level {@code L} ke {@code L+1}
 * dibutuhkan {@code BASE * L} XP, sehingga total XP menuju level {@code L}
 * adalah {@code BASE * (L-1) * L / 2}. Bentuk ini membuat kenaikan awal terasa
 * cepat lalu melandai secara wajar pada level tinggi.</p>
 *
 * <p>Pangkat mengikuti tiga jalur yang diminta: pembelian (tercepat), undangan
 * teman, dan keaktifan harian. Angka XP tiap jalur ada di {@link Config}.</p>
 */
public final class Levels {

    /** Batas atas level agar perhitungan tetap aman pada XP yang sangat besar. */
    public static final int MAX_LEVEL = 999;

    private Levels() { }

    /** Total XP yang dibutuhkan untuk mencapai sebuah level (level 1 = 0). */
    public static long xpForLevel(int level) {
        if (level <= 1) return 0;
        long l = Math.min(level, MAX_LEVEL);
        return Config.XP_LEVEL_BASE * (l - 1) * l / 2;
    }

    /** Level yang dicapai dengan jumlah XP tertentu. */
    public static int levelFor(long xp) {
        if (xp <= 0) return 1;
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++;
        return level;
    }

    /** XP yang sudah terkumpul di dalam level berjalan. */
    public static long xpIntoLevel(long xp) {
        return Math.max(0, xp - xpForLevel(levelFor(xp)));
    }

    /** XP yang dibutuhkan untuk menutup level berjalan. */
    public static long xpForNextLevel(long xp) {
        int level = levelFor(xp);
        if (level >= MAX_LEVEL) return 0;
        return xpForLevel(level + 1) - xpForLevel(level);
    }

    /** Kemajuan menuju level berikutnya, 0.0 sampai 1.0. */
    public static float progress(long xp) {
        long need = xpForNextLevel(xp);
        if (need <= 0) return 1f;
        return Math.min(1f, (float) xpIntoLevel(xp) / (float) need);
    }

    /** XP dari nilai belanja: 1 XP per Rp1.000, dibulatkan ke bawah. */
    public static long xpForPurchase(long orderTotalRupiah) {
        if (orderTotalRupiah <= 0) return 0;
        return orderTotalRupiah / Config.XP_PER_RUPIAH_UNIT * Config.XP_PURCHASE_PER_UNIT;
    }

    /**
     * XP check-in harian, bertambah untuk hari beruntun. Rantai dibatasi
     * {@link Config#XP_STREAK_MAX_DAYS} agar tidak tumbuh tanpa batas.
     */
    public static long xpForCheckin(long streakDays) {
        long streak = Math.max(1, Math.min(streakDays, Config.XP_STREAK_MAX_DAYS));
        return Config.XP_DAILY_CHECKIN + Config.XP_DAILY_STREAK * streak;
    }

    /** Nama tingkatan untuk ditampilkan. */
    public static String title(int level) {
        if (level >= 30) return "Platinum";
        if (level >= 20) return "Gold";
        if (level >= 10) return "Silver";
        if (level >= 5) return "Bronze";
        return "Pemula";
    }

    /** Label singkat, mis. {@code "Lv 4 · Bronze"}. */
    public static String label(int level) {
        return "Lv " + level + " · " + title(level);
    }
}
