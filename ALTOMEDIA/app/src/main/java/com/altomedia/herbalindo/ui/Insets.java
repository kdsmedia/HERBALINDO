package com.altomedia.herbalindo.ui;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.view.WindowInsetsControllerCompat;

import com.altomedia.herbalindo.R;

/**
 * Penyesuai tampilan tepi ke tepi (edge-to-edge).
 *
 * <p>Mulai API 35 Android mengabaikan permintaan agar tata letak tidak masuk ke
 * area sistem, sehingga bilah status dan bilah navigasi menimpa isi layar.
 * Aplikasi memakai {@code targetSdk 36}, jadi penyesuaian wajib dilakukan
 * sendiri. Atribut {@code windowOptOutEdgeToEdgeEnforcement} pada tema hanya
 * berlaku sampai API 35 dan tidak dapat diandalkan pada API 36, karena itu
 * penyesuaian berikut tetap menjadi jalur utama.</p>
 *
 * <p>Agar hasilnya sama di semua tingkat API, mode tepi ke tepi dinyalakan
 * secara tegas untuk API 30 ke atas. Di bawah API 30 tata letak bawaan sudah
 * menghindari area sistem sehingga tidak ada yang perlu ditambahkan; kalau
 * padding tetap ditambahkan di sana, ruangnya menjadi dua kali.</p>
 *
 * <p>Setiap sisipan ditambahkan ke padding yang sudah ada pada tampilan, bukan
 * menggantikannya, agar jarak asli dari XML tetap dipertahankan. Nilai awal
 * diingat lewat penanda pada tampilan sehingga sisipan yang dilaporkan
 * berulang kali tidak menumpuk.</p>
 */
public final class Insets {

    private static final int PAD_TOP = R.id.sysbar_pad_top;
    private static final int PAD_START = R.id.sysbar_pad_start;
    private static final int PAD_END = R.id.sysbar_pad_end;
    private static final int PAD_BOTTOM = R.id.sysbar_pad_bottom;

    private Insets() { }

    /** Apakah sisipan perlu ditambahkan sendiri. */
    public static boolean manual() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * Menyalakan mode tepi ke tepi. Panggil sebelum {@code setContentView} agar
     * tata letak tidak lagi disempitkan sistem dan sisipan dapat dibaca sendiri.
     */
    public static void enableEdgeToEdge(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Window window = activity.getWindow();
        window.setDecorFitsSystemWindows(false);
        // Latar di kedua ujung layar selalu gelap (header hijau tua di atas,
        // bilah navigasi aplikasi berwarna permukaan di bawah) sehingga ikon
        // bawaan yang putih tetap terbaca. Tema Material3 versi terang dapat
        // membaliknya menjadi gelap; penegasan ini menjaga hasil tetap sama.
        WindowInsetsControllerCompat c =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);
    }

    /**
     * Menaikkan tampilan sebesar sisipan bilah status dan memberi ruang bawah
     * sebesar sisipan bilah navigasi. Dipakai oleh layar yang isinya memenuhi
     * seluruh tinggi jendela dan tidak punya bilah navigasi aplikasi sendiri.
     */
    public static void applySystemBars(View view) {
        apply(view, true, true);
    }

    /**
     * Hanya memberi ruang atas sebesar sisipan bilah status. Dipakai oleh
     * aktivitas yang sudah menyisakan ruang bawah untuk bilah navigasi aplikasi
     * (mis. {@code BottomNavigationView} pada layar member).
     */
    public static void applyTopInset(View view) {
        apply(view, true, false);
    }

    private static void apply(View view, boolean top, boolean bottom) {
        if (view == null || !manual()) return;
        final int padTop = remember(view, PAD_TOP, view.getPaddingTop());
        final int padStart = remember(view, PAD_START, view.getPaddingStart());
        final int padEnd = remember(view, PAD_END, view.getPaddingEnd());
        final int padBottom = remember(view, PAD_BOTTOM, view.getPaddingBottom());
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int addTop = top ? insets.getSystemWindowInsetTop() : 0;
            int addBottom = bottom ? insets.getSystemWindowInsetBottom() : 0;
            v.setPaddingRelative(
                    padStart + insets.getSystemWindowInsetLeft(),
                    padTop + addTop,
                    padEnd + insets.getSystemWindowInsetRight(),
                    padBottom + addBottom);
            return insets;
        });
        view.requestApplyInsets();
    }

    /**
     * Padding asli dari XML dibaca sekali lalu diingat pada tampilan lewat
     * penanda {@link Integer}.
     */
    private static int remember(View view, int key, int value) {
        Object tag = view.getTag(key);
        if (tag instanceof Integer) return (Integer) tag;
        view.setTag(key, Integer.valueOf(value));
        return value;
    }
}
