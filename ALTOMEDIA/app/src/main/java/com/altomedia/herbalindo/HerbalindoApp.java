package com.altomedia.herbalindo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.altomedia.herbalindo.ads.AdsManager;
import com.altomedia.herbalindo.data.Repository;
import com.altomedia.herbalindo.ui.WhatsappPromo;

public class HerbalindoApp extends Application {

    private static HerbalindoApp instance;

    public static HerbalindoApp get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        // Menyiapkan aturan bisnis + data awal sebelum layar pertama tampil.
        Repository.get(this);
        AdsManager.get(this).init();
        // Layar yang sedang tampil dicatat di satu tempat agar popup yang
        // waktunya tiba setelah layar pembuka selesai tetap punya sasaran.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                WhatsappPromo.onActivityResumed(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {
                WhatsappPromo.onActivityPaused(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle b) { }

            @Override public void onActivityStarted(@NonNull Activity activity) { }

            @Override public void onActivityStopped(@NonNull Activity activity) { }

            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle b) { }

            @Override public void onActivityDestroyed(@NonNull Activity activity) { }
        });
    }
}