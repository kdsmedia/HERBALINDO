package com.altomedia.herbalindo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.altomedia.herbalindo.core.Session;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/**
 * Basis aktivitas member: menyediakan sesi, repositori, iklan, dan
 * helper format. Sesi diverifikasi ulang setiap kali layar dilanjutkan.
 */
public abstract class BaseActivity extends AppCompatActivity {

    public Repository repo;
    public Models.User user;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        // Dinyalakan sebelum setContentView pada subkelas agar tata letak sudah
        // disiapkan untuk area sistem sejak awal, bukan setelah tampil.
        Insets.enableEdgeToEdge(this);
        repo = Repository.get(this);
    }

    @Override protected void onResume() {
        super.onResume();
        // Popup ajakan bergabung dijadwalkan dari layar yang sedang tampil.
        // Layar pembuka selesai jauh sebelum jedanya berakhir, sehingga layar
        // berikutnya yang melanjutkan penjadwalan dengan sisa waktu yang sama.
        WhatsappPromo.schedule(this, repo);
        if (!requiresSession()) return;
        Models.User u = Session.current(this);
        if (u == null) {
            toAuth();
            return;
        }
        user = u;
        onSessionReady(u);
    }

    /**
     * Apakah layar ini menuntut sesi login. Layar yang memang tampil tanpa login
     * (mis. {@link AuthActivity}) mengembalikan {@code false}; jika tidak,
     * pemeriksaan sesi akan menutup layar tersebut tepat saat dibuka.
     */
    protected boolean requiresSession() { return true; }

    /**
     * Mengembalikan pengguna ke layar masuk. Dipakai saat sesi hilang atau akun
     * tidak lagi aktif, sehingga pengguna tidak tertahan di layar kosong.
     */
    protected void toAuth() {
        if (this instanceof AuthActivity) return;
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    protected abstract void onSessionReady(Models.User user);

    /**
     * Pemilik inset bilah status pada sebuah layar. Bawaan: akar tata letak
     * ({@code android.R.id.content}).
     *
     * <p>Sebagian layar tidak boleh menerima inset pada akar tata letaknya.
     * Layar member, misalnya, memakai {@code android:windowSoftInputMode="adjustResize"}:
     * bila akar tata letak ikut dikurangi papan tombol, isi yang bisa digulir
     * tidak menyisakan ruang saat papan tombol muncul. Karena itu layar tersebut
     * memindahkan inset ke header dan membiarkan sisanya lewat kurungan sistem
     * ({@code decorFitsSystemWindows}).</p>
     */
    protected View insetTarget() {
        View content = findViewById(android.R.id.content);
        return content != null ? content : getWindow().getDecorView();
    }

    /** {@code true} bila inset hanya ditempelkan pada bagian atas layar. */
    protected boolean insetTopOnly() { return false; }

    /**
     * Menyiapkan penyesuaian tepi ke tepi. Panggil tepat setelah
     * {@code setContentView}.
     */
    protected void applyInsets() {
        if (insetTopOnly()) {
            Insets.applyTopInset(insetTarget());
        } else {
            Insets.applySystemBars(insetTarget());
        }
    }

    /** Handler utama untuk animasi ringan (progress iklan, dsb.). */
    protected Handler ui() { return handler; }

    public Repository repo() { return repo; }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}