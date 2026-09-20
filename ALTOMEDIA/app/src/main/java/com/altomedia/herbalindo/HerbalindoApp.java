package com.altomedia.herbalindo;

import android.app.Application;

import com.altomedia.herbalindo.ads.AdsManager;
import com.altomedia.herbalindo.data.Repository;

public class HerbalindoApp extends Application {

    private static HerbalindoApp instance;

    public static HerbalindoApp get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        // Menyiapkan aturan bisnis + data awal sebelum layar pertama tampil.
        Repository.get(this);
        AdsManager.get(this).init();
    }
}