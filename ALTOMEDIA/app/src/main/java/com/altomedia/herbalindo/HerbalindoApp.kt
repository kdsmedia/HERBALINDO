package com.altomedia.herbalindo

import android.app.Application
import com.altomedia.herbalindo.data.Repository
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.util.concurrent.Executor

class HerbalindoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase init — offline persistence keeps the app light and usable on weak networks.
        FirebaseApp.initializeApp(this)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder().setSizeBytes(20L * 1024 * 1024).build()
            )
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        // AdMob initialisation is offloaded so it never blocks the first frame.
        MobileAds.initialize(this) { }

        Repository.init(this)
    }

    companion object {
        val io: Executor = Executor { it.run() }
    }
}