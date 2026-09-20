package com.altomedia.herbalindo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

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
        repo = Repository.get(this);
    }

    @Override protected void onResume() {
        super.onResume();
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

    /** Handler utama untuk animasi ringan (progress iklan, dsb.). */
    protected Handler ui() { return handler; }

    public Repository repo() { return repo; }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}