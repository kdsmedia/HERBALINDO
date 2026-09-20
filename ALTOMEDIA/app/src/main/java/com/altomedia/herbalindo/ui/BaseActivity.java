package com.altomedia.herbalindo.ui;

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
        Models.User u = Session.current(this);
        if (u == null) {
            finish();
            return;
        }
        user = u;
        onSessionReady(u);
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